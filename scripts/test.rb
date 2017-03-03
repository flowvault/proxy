#!/usr/bin/env ruby

require 'json'
require 'securerandom'

load 'helpers.rb'
load 'assert.rb'

api_key_file = File.expand_path("~/.flow/token")
if !File.exists?(api_key_file)
  puts "ERROR: Missing api key file: %s" % api_key_file
  exit(1)
end

puts "logging to %s" % ProxyGlobal::LOG_FILE
puts ""

helpers = Helpers.new("http://localhost:7000")

response = helpers.json_post("/foo").execute
assert_generic_error(response, "Unknown HTTP path /foo")

response = helpers.json_post("/foo?envelope=res").execute
assert_generic_error(response, "Invalid value for query parameter 'envelope' - must be one of request, response")

response = helpers.json_post("/token-validations").execute
assert_generic_error(response, "Missing required field for type 'token_validation_form': 'token'")

response = helpers.json_post("/token-validations", { :token => "foo" }).execute
assert_generic_error(response, "The specified API token is not valid")

response = helpers.json_post("/token-validations", { :token => IO.read(api_key_file).strip }).execute
assert_status(response, 200)
assert_equals(response.json["status"], "Hooray! The provided API Token is valid.")

response = helpers.json_post("/organizations", { :environment => 'sandbox', :parent => 'demo' }).execute
assert_unauthorized(response)

response = helpers.json_post("/organizations", { :environment => 'sandbox', :parent_id => 'flow', :name => "Proxy Test" }).with_api_key_file(api_key_file).execute
assert_unauthorized(response)

