FROM enclave-base

RUN yum install net-tools -y

ADD run.sh /app

RUN chmod +x run.sh

ENTRYPOINT ["/app/run.sh"]