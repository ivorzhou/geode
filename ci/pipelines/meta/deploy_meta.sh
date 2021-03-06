#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# ./deploy_meta.sh <repo-fork> <repo-name> <upstream-fork> <concourse-host> <artifact bucket> <public true/false>

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  SCRIPTDIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
SCRIPTDIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"

for cmd in Jinja2 PyYAML; do
  if ! [[ $(pip3 list |grep ${cmd}) ]]; then
    echo "${cmd} must be installed for pipeline deployment to work."
    echo " 'pip3 install ${cmd}'"
    echo ""
    exit 1
  fi
done

if [ -z $(command -v gcloud) ]; then
  echo "Install gcloud (try brew cask install google-cloud-sdk, or see https://cloud.google.com/sdk/docs/downloads-interactive#mac)"
  exit 1
fi

GCP_PROJECT=${GCP_PROJECT:-$(gcloud info --format="value(config.project)")}
echo "GCP_PROJECT=${GCP_PROJECT}"
if [ -z ${GCP_PROJECT} ]; then
  echo "GCP_PROJECT not set. Quitting"
  exit 1
fi

set -e
set -x

GEODE_FORK=${1:-"apache"}
GEODE_REPO_NAME=${2:-"geode"}
UPSTREAM_FORK=${3:-"apache"}
CONCOURSE_HOST=${4:-"concourse.apachegeode-ci.info"}
ARTIFACT_BUCKET=${5:-"files.apachegeode-ci.info"}
PUBLIC=${6:-"true"}
REPOSITORY_PUBLIC=${7:-"true"}
if [[ "${CONCOURSE_HOST}" == "concourse.apachegeode-ci.info" ]]; then
  CONCOURSE_SCHEME=https
fi
CONCOURSE_URL=${CONCOURSE_SCHEME:-"http"}://${CONCOURSE_HOST}
FLY_TARGET=${CONCOURSE_HOST}
GEODE_BRANCH=$(git rev-parse --abbrev-ref HEAD)

. ${SCRIPTDIR}/../shared/utilities.sh
SANITIZED_GEODE_BRANCH=$(getSanitizedBranch ${GEODE_BRANCH})
SANITIZED_GEODE_FORK=$(getSanitizedFork ${GEODE_FORK})

echo "Deploying pipline for ${GEODE_FORK}/${GEODE_BRANCH}"

META_PIPELINE="${SANITIZED_GEODE_FORK}-${SANITIZED_GEODE_BRANCH}-meta"
PIPELINE_PREFIX="${SANITIZED_GEODE_FORK}-${SANITIZED_GEODE_BRANCH}-"

if [[ "${GEODE_FORK}" != "${UPSTREAM_FORK}" ]]; then
  PUBLIC=false
fi

pushd ${SCRIPTDIR} 2>&1 > /dev/null
# Template and output share a directory with this script, but variables are shared in the parent directory.
  python3 ../render.py $(basename ${SCRIPTDIR}) ${GEODE_FORK} ${GEODE_BRANCH} ${UPSTREAM_FORK} ${REPOSITORY_PUBLIC} || exit 1

  fly -t ${FLY_TARGET} sync
  fly -t ${FLY_TARGET} set-pipeline \
    -p ${META_PIPELINE} \
    --config ${SCRIPTDIR}/generated-pipeline.yml \
    --var concourse-team="main" \
    --var concourse-url=${CONCOURSE_URL} \
    --var artifact-bucket=${ARTIFACT_BUCKET} \
    --var gcp-project=${GCP_PROJECT} \
    --var geode-build-branch=${GEODE_BRANCH} \
    --var sanitized-geode-build-branch=${SANITIZED_GEODE_BRANCH} \
    --var sanitized-geode-fork=${SANITIZED_GEODE_FORK} \
    --var geode-fork=${GEODE_FORK} \
    --var geode-repo-name=${GEODE_REPO_NAME} \
    --var upstream-fork=${UPSTREAM_FORK} \
    --var pipeline-prefix=${PIPELINE_PREFIX} \
    --var concourse-team=main \
    --yaml-var public-pipelines=${PUBLIC} 2>&1 |tee flyOutput.log

popd 2>&1 > /dev/null


if [[ "$(tail -n1 flyOutput.log)" == "bailing out" ]]; then
  exit 1
fi

# bootstrap all precursors of the actual Build job

function jobStatus {
  PIPELINE=$1
  JOB=$2
  fly jobs -t ${FLY_TARGET} -p ${PIPELINE}|awk "/${JOB}/"'{if($2=="yes")print "paused";else if($4!="n/a")print $4; else print $3}'
}

function triggerJob {
  PIPELINE=$1
  JOB=$2
  (set -x ; fly trigger-job -t ${FLY_TARGET} -j ${PIPELINE}/${JOB})
}

function pauseJob {
  PIPELINE=$1
  JOB=$2
  (set -x ; fly pause-job -t ${FLY_TARGET} -j ${PIPELINE}/${JOB})
}

function pauseJobs {
  PIPELINE=$1
  shift
  for JOB; do
    pauseJob $PIPELINE $JOB
  done
}

function pauseNewJobs {
  PIPELINE=$1
  shift
  for JOB; do
    STATUS="$(jobStatus $PIPELINE $JOB)"
    [[ "$STATUS" == "n/a" ]] && pauseJob $PIPELINE $JOB
  done
}

function unpauseJob {
  PIPELINE=$1
  JOB=$2
  (set -x ; fly unpause-job -t ${FLY_TARGET} -j ${PIPELINE}/${JOB})
}

function unpauseJobs {
  PIPELINE=$1
  shift
  for JOB; do
    unpauseJob $PIPELINE $JOB
  done
}

function unpausePipeline {
  PIPELINE=$1
  (set -x ; fly -t ${FLY_TARGET} unpause-pipeline -p ${PIPELINE})
}

function awaitJob {
  PIPELINE=$1
  JOB=$2
  echo -n "Waiting for ${JOB}..."
  status="n/a"
  while [ "$status" = "n/a" ] || [ "$status" = "started" ] ; do
    echo -n .
    sleep 5
    status=$(jobStatus ${PIPELINE} ${JOB})
  done
  echo $status
  [ "$status" = "succeeded" ] || return 1
}

function driveToGreen {
  PIPELINE=$1
  JOB=$2
  status=$(jobStatus ${PIPELINE} ${JOB})
  if [ "paused" = "$status" ] ; then
    unpauseJob ${PIPELINE} ${JOB}
    status=$(jobStatus ${PIPELINE} ${JOB})
  fi
  if [ "aborted" = "$status" ] || [ "failed" = "$status" ] || [ "errored" = "$status" ] ; then
    triggerJob ${PIPELINE} ${JOB}
    awaitJob ${PIPELINE} ${JOB}
  elif [ "n/a" = "$status" ] || [ "started" = "$status" ] ; then
    awaitJob ${PIPELINE} ${JOB}
  elif [ "succeeded" = "$status" ] ; then
    echo "${JOB} $status"
    return 0
  else
    echo "Unrecognized job status for ${PIPELINE}/${JOB}: $status"
    exit 1
  fi
}

set -e
set +x

if [[ "${GEODE_FORK}" != "${UPSTREAM_FORK}" ]]; then
  echo "Disabling unnecessary jobs for forks."
  pauseJobs ${META_PIPELINE} set-images set-reaper
elif [[ "$GEODE_FORK" == "apache" ]] && [[ "$GEODE_BRANCH" == "develop" ]]; then
  echo "Disabling optional jobs for develop"
  pauseNewJobs set-pr set-images set-metrics set-examples
else
  echo "Disabling unnecessary jobs for release branches."
  echo "*** DO NOT RE-ENABLE THESE META-JOBS ***"
  pauseJobs set-pr set-images set-reaper set-metrics set-examples
fi

unpausePipeline ${META_PIPELINE}
driveToGreen $META_PIPELINE build-meta-mini-docker-image
driveToGreen $META_PIPELINE set-images-pipeline
unpausePipeline ${PIPELINE_PREFIX}images
driveToGreen ${PIPELINE_PREFIX}images build-google-geode-builder
driveToGreen ${PIPELINE_PREFIX}images build-google-windows-geode-builder
driveToGreen $META_PIPELINE set-pipeline
unpausePipeline ${PIPELINE_PREFIX}main
echo "Successfully deployed ${CONCOURSE_URL}/teams/main/pipelines/${PIPELINE_PREFIX}main"

if [[ "$GEODE_FORK" == "apache" ]] && [[ "$GEODE_BRANCH" == "develop" ]]; then
  unpauseJobs set-pr set-metrics set-examples
fi