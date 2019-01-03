#!/usr/bin/env ruby

require 'json'
require 'securerandom'

load 'helpers.rb'
load 'assert.rb'

PARENT_ORGANIZATION_ID = "flow"
TEST_ORG_PREFIX = "proxy-test"

pretty_path = "~/.flow/%s-sandboxes" % PARENT_ORGANIZATION_ID
api_key_file = File.expand_path(pretty_path)
if !File.exists?(api_key_file)
  puts "ERROR: Missing api key file: %s" % pretty_path
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

def wait_for_status(description, status, interval = 5, seconds = 120, &block)
  puts "Waiting for %s. interval[%s seconds] max[%s seconds]" % [description, interval, seconds]
  finish = Time.now.to_i + seconds

  response = nil
  while response.nil? && Time.now.to_i < finish
    sleep(interval)
    r = block.call
    if r.status == status
      response = r
    end
  end

  if response.nil?
    raise "Failed to get a status[%s] after % seconds" % [status, seconds]
  end

  response
end

uri = ARGV.shift.to_s.strip
if uri == ""
  uri = "http://localhost:7000"
end

helpers = Helpers.new(uri, api_key_file)

# One deploy had an error where these endpoints began returning 401 - test for that now.
response = helpers.get("/demo/catalog/subcatalogs").with_api_key.execute
subcatalog_id = response.json.first['id']
assert_not_nil(subcatalog_id)

assert_status(200, helpers.get("/demo/catalog/subcatalogs/#{subcatalog_id}/queries").with_api_key.execute)
assert_status(200, helpers.get("/demo/catalog/subcatalogs/#{subcatalog_id}/statistics").with_api_key.execute)

response = helpers.json_post("/demo/experiences/query/builders", { :discriminator => "query", :q => "test" }).with_api_key.execute
assert_status(201, response)

# Create a org for remainder of tests
id = "%s-%s" % [TEST_ORG_PREFIX, ProxyGlobal.random_string(8)]
response = helpers.json_post("/organizations", { :environment => 'sandbox', :parent_id => 'flow', "id" => id }).with_api_key.execute
assert_status(201, response)
assert_equals(response.json['id'], id)
org = response.json

# Return error on invalid content type - curl assumes url form encoded
response = helpers.json_request("POST", "/token-validations", { :token => IO.read(api_key_file).strip }).execute()
assert_generic_error(response, "The content type you specified 'application/x-www-form-urlencoded' does not match the body. Please specify 'Content-type: application/json' when providing a JSON Body.")

# We convert application/octet-stream to application/json
response = helpers.json_request("POST", "/sessions/organizations/demo", {}).with_content_type("application/octet-stream").execute()
assert_status(201, response)
assert_equals(response.json["organization"], "demo")

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
assert_generic_error(response, "Error in envelope request body: Field 'method' must be one of GET, POST, PUT, PATCH, DELETE, HEAD, CONNECT, OPTIONS, TRACE")

response = helpers.json_post("/organizations/#{id}?envelope=request", { :method => "GET" }).with_api_key.execute
assert_status(200, response)

# Start session testing
response = wait_for_status("Org to propagate to session", 201) { helpers.json_post("/sessions/organizations/#{id}").execute }
assert_status(201, response)
session_id = response.json['id']
assert_not_nil(session_id)

response = wait_for_status("Org countries endpoint", 200) { helpers.get("/#{id}/countries").with_header("Authorization", "session %s" % session_id).execute }
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

puts "Tests Complete. Starting cleanup"

cleanup(helpers)

puts ""
puts ""
puts "API Proxy Tests against %s" % uri
puts " All tests Passed"
puts ""
puts ""
