echo "vm.nr_hugepages=1536" | sudo tee /etc/sysctl.d/99-nitro.conf; sudo sysctl -p /etc/sysctl.d/99-nitro.conf
sudo grep Huge /proc/meminfo
nitro-cli --version
sudo systemctl start nitro-enclaves-allocator.service && sudo systemctl enable nitro-enclaves-allocator.service
sudo systemctl start docker && sudo systemctl enable docker
touch Dockerfile
echo "FROM epahomov/docker-spark:lightweighted
" >> Dockerfile
docker build . -t enclave-image:latest
nitro-cli build-enclave --docker-uri enclave-image:latest  --output-file sample.eif
nitro-cli run-enclave --cpu-count 2 --memory 3072 --eif-path sample.eif --enclave-cid 10
nitro-cli console --enclave-id 10
exit
