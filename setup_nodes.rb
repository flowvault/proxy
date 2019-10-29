#!/usr/bin/env ruby

# Simple script to make sure you are logged in on docker and deploy script is
# on the relevant nodes.
#
# Important: Make sure you have these environment variables set:
#   `DOCKER_USERNAME`: Your username for hub.docker.com
#   `DOCKER_PASSWORD`: Your password for the username account on hub.docker.com
#
# Usage:
#  ./setup_nodes.rb
#

if ENV['DOCKER_USERNAME'].nil? || ENV['DOCKER_PASSWORD'].nil?
  raise "DOCKER_USERNAME and DOCKER_PASSWORD must be valid environment variables"
end

dir = File.dirname(__FILE__)
nodes_file = File.join(dir, 'nodes')

if !File.exists?(nodes_file)
  puts "ERROR: Nodes configuration file[%s] not found" % nodes_file
  exit(1)
end

nodes = IO.readlines(nodes_file).map(&:strip).select { |l| !l.empty? }

nodes.each do |ip|
  cmd = <<TXT
scp deploy-proxy.sh #{ip}:~/.
TXT
  puts cmd
  `#{cmd}`

  cmd = <<TXT
ssh #{ip} "docker login --username $DOCKER_USERNAME --password $DOCKER_PASSWORD"
TXT
  puts cmd
  `#{cmd}`
end
