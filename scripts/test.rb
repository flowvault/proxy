#!/usr/bin/env ruby

require 'json'
require 'securerandom'

load 'helpers.rb'
load 'assert.rb'

PARENT_ORGANIZATION_ID = "flow"
TEST_ORG_PREFIX = "proxy-test"

api_key_file = File.expand_path("~/.flow/%s-sandboxes" % PARENT_ORGANIZATION_ID)
if !File.exists?(api_key_file)
  puts "ERROR: Missing api key file: %s" % api_key_file
  exit(1)
end

puts "logging to %s" % ProxyGlobal::LOG_FILE
puts ""

## Deletes organizations with a given name prefix.
## Does not currently paginate
def delete_test_orgs(helpers, parent, prefix)
  helpers.get("/organizations?limit=100&environment=sandbox&parent=#{parent}").with_api_key.execute.json.each do |r|
    if r['id'].start_with?(prefix)
      assert_statuses([204, 404], helpers.delete("/organizations/#{r['id']}").with_api_key.execute)
    end
  end
end

def cleanup(helpers)
  delete_test_orgs(helpers, PARENT_ORGANIZATION_ID, TEST_ORG_PREFIX)
end

uri = ARGV.shift.to_s.strip
if uri == ""
  uri = "http://localhost:7000"
end

helpers = Helpers.new(uri, api_key_file)

id = "%s-%s" % [TEST_ORG_PREFIX, ProxyGlobal.random_string(8)]
response = helpers.json_post("/organizations", { :environment => 'sandbox', :parent_id => 'flow', "id" => id }).with_api_key.execute
assert_status(201, response)
assert_equals(response.json['id'], id)
org = response.json

# Test unknown path and response envelopes
response = helpers.json_post("/foo").execute
assert_generic_error(response, "HTTP path '/foo' is not defined")

response = helpers.json_post("/foo?envelope=res").execute
assert_generic_error(response, "Invalid value 'res' for query parameter 'envelope' - must be one of request, response")

response = helpers.json_post("/foo?envelope=response").execute
assert_envelope(response)
assert_generic_error(response.unwrap_envelope, "HTTP path '/foo' is not defined")

response = helpers.json_post("/foo?envelope=response&callback=cb").execute
assert_jsonp(response, "cb")
assert_generic_error(response.unwrap_jsonp, "HTTP path '/foo' is not defined")

response = helpers.json_post("/token-validations").execute
assert_generic_error(response, "Missing required field for token_validation_form: token")

response = helpers.json_post("/token-validations", { :token => "foo" }).execute
assert_generic_error(response, "The specified API token is not valid")

response = helpers.json_post("/token-validations", { :token => IO.read(api_key_file).strip }).execute
assert_status(200, response)
assert_equals(response.json["status"], "Hooray! The provided API Token is valid.")

response = helpers.json_put("/organizations/#{id}", { :environment => 'sandbox', :parent => 'demo' }).execute
assert_status(401, response)

# Validate we cannot access another organization
response = helpers.get("/organizations/remolacha").with_api_key.execute
assert_status(422, response)
assert_equals(response.json["messages"], ["Not authorized to access organization 'remolacha' or the organization does not exist"])

# Validate can access own organization
response = helpers.get("/organizations/#{id}").with_api_key.execute
assert_status(200, response)

# Test response envelopes for valid requests
response = helpers.get("/organizations/#{id}?envelope=response").with_api_key.execute
assert_envelope(response)
r = response.unwrap_envelope
assert_status(200, r)
assert_equals(r.json['id'], id)

response = helpers.get("/organizations/#{id}?envelope=response&callback=foo").with_api_key.execute
assert_jsonp(response, "foo")
r = response.unwrap_jsonp
assert_status(200, r)
assert_equals(r.json['id'], id)

# Test request envelope
response = helpers.json_post("/organizations/0?envelope=request", { }).with_api_key.execute
assert_generic_error(response, "Error in envelope request body: Field 'method' is required")

response = helpers.json_post("/organizations/0?envelope=request", { :method => 123, :body => "test" }).with_api_key.execute
assert_generic_error(response, "Error in envelope request body: Field 'method' must be one of GET, POST, PUT, PATCH, DELETE")

new_name = org['name'] + " 2"
response = helpers.json_post("/organizations/#{id}?envelope=request", { :method => "PUT", :body => { :name => new_name } }).with_api_key.execute
assert_unauthorized(response)

response = helpers.json_post("/organizations/#{id}?envelope=request", { :method => "GET" }).with_api_key.execute
assert_unauthorized(response)

# Start session testing
seconds = 15
puts "Waiting %s seconds for organization to propagate to session service itself" % seconds
sleep(seconds)

response = helpers.json_post("/sessions/organizations/#{id}").execute
assert_status(201, response)
session_id = response.json['id']
assert_not_nil(session_id)

response = helpers.get("/#{id}/countries").with_header("Authorization", "session %s" % session_id).execute
assert_status(200, response)

response = helpers.get("/#{id}/countries").execute
assert_unauthorized(response)

response = helpers.new_request("POST", "/#{id}/countries?envelope=request").
             with_body(
               ProxyGlobal.format_json(
                 :method => "GET", "headers" => { "Authorization" => ["Session #{session_id}"] }
               )
             ).execute
assert_status(200, response)

response = helpers.new_request("POST", "/#{id}/countries?envelope=request").
             with_body(
               ProxyGlobal.format_json(
                 :method => "GET"
               )
             ).execute
assert_unauthorized(response)

puts "Tests Complete. Starting cleanup"

cleanup(helpers)

puts ""
puts ""
puts "API Proxy Tests against %s" % uri
puts " All tests Passed"
puts ""
puts ""
