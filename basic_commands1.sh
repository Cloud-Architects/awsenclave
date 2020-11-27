echo "Programmatically SSHed into the instance."
sudo amazon-linux-extras enable aws-nitro-enclaves-cli
sudo amazon-linux-extras enable docker
sudo yum install docker aws-nitro-enclaves-cli aws-nitro-enclaves-cli-devel -y
sudo usermod -aG ne ec2-user
sudo usermod -aG docker ec2-user
echo "vm.nr_hugepages=1536" | sudo tee /etc/sysctl.d/99-nitro.conf; sudo sysctl -p /etc/sysctl.d/99-nitro.conf
sudo reboot
exit
