#!/usr/bin/env ruby

# Reads the nodes file to tell you what needs to be done as part of one time setup
#
# Usage:
#  install-scripts.rb
#

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

puts "To setup deploy scripts on each node in the proxy cluster:\n\n"

nodes.each do |n|
  puts "  scp deploy-proxy.sh #{n}:~/."
end

puts "\n"
puts "To setup your user permissions:\n\n"

username = `whoami`.strip
nodes.each do |n|
  puts "  ssh #{n} sudo usermod -a -G docker #{username}"
end



puts ""
puts "Once installed, run ./deploy.rb"
