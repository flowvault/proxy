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

nodes_file = ARGV.shift.to_s.strip
if nodes_file.empty?
  dir = File.dirname(__FILE__)
  nodes_file = File.join(dir, 'nodes')
end

if !File.exists?(nodes_file)
  puts "ERROR: Nodes configuration file[%s] not found" % nodes_file
  exit(1)
end

nodes = IO.readlines(nodes_file).map(&:strip).select { |l| !l.empty? }
if nodes.empty?
  puts "ERROR: Nodes configuration file[%s] is empty" % nodes_file
  exit(1)
end

puts "Docker login:\n\n"
nodes.each do |n|
  cmd = <<TXT
ssh #{n} "docker login --username $DOCKER_USERNAME --password $DOCKER_PASSWORD"
TXT
  puts cmd
  `#{cmd}`
end

puts "Deploy Scripts to cluster:\n\n"
nodes.each do |n|
  cmd = "  scp deploy-proxy.sh #{n}:~/."
  puts cmd
  `#{cmd}`
end

puts "\n"
puts "To setup your user permissions:\n\n"
username = `whoami`.strip
nodes.each do |n|
  puts "  ssh #{n} sudo usermod -a -G docker #{username}"
end

puts ""
puts "Once installed, run ./deploy.rb"
