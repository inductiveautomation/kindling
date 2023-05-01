FROM debian:latest

RUN wget https://downloads.hydraulic.dev/conveyor/hydraulic-conveyor_8.1_amd64.deb \
    && apt install ./hydraulic-conveyor_8.1_amd64.deb

ENTRYPOINT /bin/sh
