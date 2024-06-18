#!/bin/bash

if [ $# -lt 3 ]
  then
    echo "./run.sh <projectFolder> <outFolderCpg> <outFolderConsumer> <optional:workerCount>"
    exit 1
fi

projectFolder=$1
outFolderCpg=$2
outFolderConsumer=$3
worker=""
if [ $# -eq 4 ]
  then
    worker="--worker $4"
fi

docker run -v ${projectFolder}:/in/ \
  -v ${outFolderCpg}:/out/cpg/ \
  -v ${outFolderConsumer}:/out/scama/ \
  scala-master run $worker