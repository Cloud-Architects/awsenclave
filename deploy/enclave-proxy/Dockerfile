FROM enclave-base

RUN yum install net-tools -y

ADD run.sh /app

RUN chmod +x /app/run.sh

ENV AWS_REGION ENCLAVE_REGION

ENTRYPOINT ["/app/run.sh"]