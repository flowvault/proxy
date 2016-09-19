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

def wait_for_healthcheck(uri, timeout_ms=5000, sleep_between_internal_ms=500, started_at=Time.now)
  url = URI.parse(uri)
  req = Net::HTTP::Get.new(url.to_s)

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
    if duration*1000 > timeout_ms
      puts "ERROR: Timeout waiting for healthcheck: #{uri}"
      exit(1)
    end
  
    puts "  - waiting for healthcheck. sleeping for %s ms" % sleep_between_internal_ms
    sleep(sleep_between_internal_ms / 1000.0)

    wait_for_healthcheck(uri, timeout_ms, sleep_between_internal_ms, started_at)
  end
end

nodes.each do |node|
  puts node
  puts "  - Deploying version #{version}"
  deploy(node, version)
  wait_for_healthcheck("http://#{node}:#{PORT}/_internal_/healthcheck")
  puts ""
end
