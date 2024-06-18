#!/bin/bash

id=$(docker images | grep '^scala-master' | awk '{print $3}')
docker tag ${id} git.ias.cs.tu-bs.de/simon.koch/scala-master:latest
docker push git.ias.cs.tu-bs.de/simon.koch/scala-master:latest
