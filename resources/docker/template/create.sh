#!/bin/bash

DOCKER_BUILDKIT=1 docker build --no-cache --ssh default -t scala-master .
