FROM multilayer-cpg-php:latest AS scala-master

ENV SCAMA_REPOSITORY_URL="https://github.com/PHP-CPG/scala-master.git"
ENV SCAMA_CONFIG="main.conf"
ENV TELEGRAM_CONFIG="telegram.conf*"

WORKDIR /
RUN mkdir /in/
RUN mkdir -p /out/
RUN mkdir /out/scama/
RUN mkdir /out/cpg/

WORKDIR /
RUN git clone ${SCAMA_REPOSITORY_URL}
WORKDIR /scala-master/
RUN sbt stage
COPY ${SCAMA_CONFIG} "main.conf"
COPY "dummy.file" ${TELEGRAM_CONFIG}
COPY "cpg.conf" "/php-cpg/main.conf"
WORKDIR /
ENTRYPOINT ["/scala-master/run.sh","/scala-master/main.conf"]
