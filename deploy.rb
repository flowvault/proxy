#!/usr/bin/env ruby

# Deploys our proxy servers running in the PCI environment including
# our [API Proxy](http://github.com/flowvault/proxy) and 
# [GraphQL Proxy](http://github.com/flowvault/graphql)
#
# Usage:
#  deploy.rb <application_name> <version> (<nodes file>)
#    - application_name: "proxy" or "graphql"
#    - version: e.g. 0.6.46
#    - nodes_file (optional): defaults to "./nodes". This file
#      contains a list of ip addresses which are the servers
#      on which we deploy.
#
#  Examples:
#    - deploy.rb proxy 0.0.44
#    - deploy.rb graphql 1.2.4 /tmp/nodes
#
# Deploy Process:
#
#  ssh to each server in order and execute ./install-instance.sh <image>
#    - example: ./install-instance.sh flowvault/proxy:0.6.46
#
#  This script will stop the old image and start the new image
#  and will wait for the healthcheck (expected at /_internal_/healthcheck
#  to succeed.
#   - if fails, stop the release
#   - if succeeds, continue
#

require 'uri'
require 'net/http'
require 'json'
require 'logger'

class Application
  attr_reader :name
  def initialize(name)
    @name = name
  end
  def image
    "flowvault/%s" % name
  end
end

class MultiLog
  def initialize(*targets)
     @targets = targets
  end

  def write(*args)
    @targets.each {|t| t.write(*args)}
  end

  def close
    @targets.each(&:close)
  end
end

# output to both log file and std out
log_file_path = "/tmp/deploy-#{Time.now.strftime("%Y%m%d%H%M%S")}.log"
log_file = File.open(log_file_path, "a")
LOGGER = Logger.new(MultiLog.new(STDOUT, log_file))

def latest_tag(owner,repo)
  cmd = "curl --silent https://api.github.com/repos/#{owner}/#{repo}/tags"
  if latest = JSON.parse(`#{cmd}`)
    if latest.is_a?(Array)
      value = latest.first['name'].to_s.strip
      value.empty? ? nil : value
    else
      nil
    end
  else
    nil
  end
end

Applications = [Application.new("graphql"), Application.new("proxy")]

module Console
  def Console.ask(message, opts = {})
    default = opts[:default]
    m = "#{message}"
    if default
      m << " Default[#{default}]"
    end
    print "#{m}: "
    input = $stdin.gets.strip
    if input.strip.empty?
      input = default
    end
    if input.to_s.strip.empty?
      LOGGER.info "\nEnter a valid value.\n"
      Console.ask(message, opts)
    else
      input
    end
  end
end

application = ARGV.shift.to_s.strip
version = ARGV.shift.to_s.strip
nodes_file = ARGV.shift.to_s.strip
if nodes_file.empty?
  dir = File.dirname(__FILE__)
  nodes_file = File.join(dir, 'nodes')
end

app = Applications.find { |a| a.name == application }
while app.nil?
  if !application_name.empty?
    LOGGER.info "ERROR: Invalid application[%s]. Must be one of: %s" % [application, Applications.map(&:name)]
  end
  application = Console.ask("Specify application (%s)" % [Applications.map(&:name).join(", ")])
  app = Applications.find { |a| a.name == application }
end

if version.empty?
  default = latest_tag("flowvault", app.name)
  while version.empty?
    version = Console.ask("Specify version to deploy", :default => default)
  end
end

if !File.exists?(nodes_file)
  LOGGER.info "ERROR: Nodes configuration file[%s] not found" % nodes_file
  exit(1)
end

nodes = IO.readlines(nodes_file).map(&:strip).select { |l| !l.empty? }
if nodes.empty?
  LOGGER.info "ERROR: Nodes configuration file[%s] is empty" % nodes_file
  exit(1)
end

# Installs and starts software on an instance
def install_instance(node, app, version)
  cmd = "ssh #{node} ./install-instance.sh #{app.image}:#{version}"
  LOGGER.info "==> #{cmd}"

  begin
    output = `#{cmd}`
    LOGGER.info output
  rescue Exception => e
    LOGGER.info "ERROR running cmd: #{e.message}"
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
      LOGGER.info " (#{duration.to_i} seconds)"
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

  LOGGER.info "\nERROR: Timeout exceeded[%s seconds]" % timeout_seconds
  exit(1)
end

LOGGER.info "Logs found in: #{log_file_path}"

timeout = 50
start = Time.now
nodes.each_with_index do |node, index|
  LOGGER.info node
  label = "node #{index+1}/#{nodes.size}"
  LOGGER.info "  - Deploying #{app.image}:#{version} to #{label}"
  install_instance(node, app, version)

  uri = "http://#{node}:#{PORT}/_internal_/healthcheck"
  url = URI.parse(uri)
  req = Net::HTTP::Get.new(url.to_s)

  LOGGER.info "  - Checking health of #{label}"
  LOGGER.info "    #{uri} (timeout #{timeout} seconds)"
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

  LOGGER.info ""
end
duration = Time.now - start

LOGGER.info ""
LOGGER.info "Logs found in: #{log_file_path}"
LOGGER.info "Application %s version %s deployed successfully. Total duration: %s seconds" % [app.name, version, duration.to_i]
