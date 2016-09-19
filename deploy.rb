#!/usr/bin/env ruby

# Simple ruby script that we use to deploy a new version of the proxy
# server. Process is:
#
#  ssh to each server in order and execute ./deploy-proxy.sh <version>
#  wait for healthcheck to succeed
#   - if fails, stop the release
#   - if succeeds, continue
#
# Usage:
#  deploy.rb 0.0.44
#    - reads node to deploy from ./nodes
#
#   ./deploy.rb 0.0.44 /tmp/nodes
#    - reads node to deploy fron /tmp/nodes
#

require 'uri'
require 'net/http'

PORT = 7000

version = ARGV.shift.to_s.strip
if version.empty?
  puts "ERROR: Specify version to deploy"
  exit(1)
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

# Installs and starts software
def deploy(node, version)
  cmd = "ssh #{node} ./deploy-proxy.sh #{version}"
  puts "==> #{cmd}"
  if !system(cmd)
    puts "ERROR running cmd: #{cmd}"
    exit(1)
  end
end

def wait_for_healthcheck(uri, timeout_seconds=30, sleep_between_interval_seconds=1, started_at=Time.now)
  url = URI.parse(uri)
  req = Net::HTTP::Get.new(url.to_s)

  puts "  - Checking health: #{uri}"  
  body = begin
           res = Net::HTTP.start(url.host, url.port) {|http|
             http.request(req)
           }
           res.body.strip
         rescue Exception => e
           nil
         end

  if body && body.match(/healthy/)
    puts "  - healthy"
  else
    duration = Time.now - started_at
    if duration > timeout_seconds
      puts "ERROR: Timeout exceeded[%s seconds] waiting for healthcheck: %s" % [timeout_seconds, uri]
      exit(1)
    end
  
    puts "  - waiting for healthcheck to succeed. timeout[%s seconds]. sleeping for %s seconds" % [timeout_seconds, sleep_between_interval_seconds]
    sleep(sleep_between_interval_seconds)

    wait_for_healthcheck(uri, timeout_seconds, sleep_between_interval_seconds, started_at)
  end
end

start = Time.now
nodes.each do |node|
  puts node
  puts "  - Deploying version #{version}"
  deploy(node, version)
  wait_for_healthcheck("http://#{node}:#{PORT}/_internal_/healthcheck")
  puts ""
end
duration = Time.now - start

puts "Proxy version %s deployed successfully. Total duration: %s seconds" % [version, duration.to_i]
