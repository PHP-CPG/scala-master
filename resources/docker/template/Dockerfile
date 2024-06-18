FROM multilayer-cpg-php:latest as scala-master

ENV SCAMA_REPOSITORY_URL "gogs@git.ias.cs.tu-bs.de:simon.koch/scala-master.git"
ENV SCAMA_CONFIG "main.conf"
ENV TELEGRAM_CONFIG "telegram.conf*"

WORKDIR /
RUN mkdir /in/
RUN mkdir -p /out/
RUN mkdir /out/scama/
RUN mkdir /out/cpg/

RUN mkdir -p -m 0600 ~/.ssh && ssh-keyscan git.ias.cs.tu-bs.de >> ~/.ssh/known_hosts

WORKDIR /
RUN --mount=type=ssh git clone ${SCAMA_REPOSITORY_URL}
WORKDIR /scala-master/
RUN sbt stage
COPY ${SCAMA_CONFIG} "main.conf"
COPY "dummy.file" ${TELEGRAM_CONFIG}
COPY "cpg.conf" "/php-cpg/main.conf"
WORKDIR /
ENTRYPOINT ["/scala-master/run.sh","/scala-master/main.conf"]
