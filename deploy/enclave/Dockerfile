FROM enclave-base

RUN yum install net-tools -y

ADD run.sh /app

RUN chmod +x /app/run.sh

ENTRYPOINT ["/app/run.sh"]