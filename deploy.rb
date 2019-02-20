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
require 'json'

PORT = 7000

def latest_tag(owner,repo)
  cmd = "curl --silent https://api.github.com/repos/#{owner}/#{repo}/tags"
  if latest = JSON.parse(`#{cmd}`).first
    latest['name'].to_s.strip
  else
    nil
  end
end

version = ARGV.shift.to_s.strip
if version.empty?
  default = latest_tag("flowvault", "proxy")
  default_message = default ? " Default[#{default}]" : nil
  
  while version.empty?
    print "Specify version to deploy#{default_message}: "
    version = $stdin.gets.strip
    if version.strip.empty?
      version = default
    end
    if version.to_s.strip.empty?
      puts "\nEnter a valid version\n"
    end
  end
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

def wait(timeout_seconds = 50, &check_function)
  sleep_between_interval_seconds = 1
  started_at = Time.now
  i = 0

  while true
    if check_function.call
      return
    end
    
    duration = Time.now - started_at
    if i % 10 == 0 && i > 0
      puts " (#{duration.to_i} seconds)"
      print "    "
    end

    if duration > timeout_seconds
      break
    end

    if i == 0
      print "    "
    end
    print "."
    i += 1
    sleep(1)
  end

  puts "\nERROR: Timeout exceeded[%s seconds]" % timeout_seconds
  exit(1)
end

timeout = 50
start = Time.now
nodes.each_with_index do |node, index|
  puts node
  label = "node #{index+1}/#{nodes.size}"
  puts "  - Deploying version #{version} to #{label}"
  deploy(node, version)

  uri = "http://#{node}:#{PORT}/_internal_/healthcheck"
  url = URI.parse(uri)
  req = Net::HTTP::Get.new(url.to_s)

  puts  "  - Checking health of #{label}"
  puts  "    #{uri} (timeout #{timeout} seconds)"
  wait(timeout) do
    begin
      res = Net::HTTP.start(url.host, url.port) { |http|
        http.request(req)
      }
      res.body.strip.match(/healthy/)
    rescue Exception => e
      false
    end
  end

  puts ""
end
duration = Time.now - start

puts ""
puts "Proxy version %s deployed successfully. Total duration: %s seconds" % [version, duration.to_i]
