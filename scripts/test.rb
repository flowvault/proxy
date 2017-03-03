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

PARENT_ORGANIZATION_ID = "flow"
TEST_ORG_PREFIX = "proxy-test"

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

helpers = Helpers.new("http://localhost:7000", api_key_file)

response = helpers.json_post("/foo").execute
assert_generic_error(response, "Unknown HTTP path /foo")

response = helpers.json_post("/foo?envelope=res").execute
assert_generic_error(response, "Invalid value for query parameter 'envelope' - must be one of request, response")

response = helpers.json_post("/token-validations").execute
assert_generic_error(response, "Missing required field for type 'token_validation_form': 'token'")

response = helpers.json_post("/token-validations", { :token => "foo" }).execute
assert_generic_error(response, "The specified API token is not valid")

response = helpers.json_post("/token-validations", { :token => IO.read(api_key_file).strip }).execute
assert_status(200, response)
assert_equals(response.json["status"], "Hooray! The provided API Token is valid.")

response = helpers.json_post("/organizations", { :environment => 'sandbox', :parent => 'demo', :id => "proxy-test" }).execute
assert_unauthorized(response)

id = "%s-%s" % [TEST_ORG_PREFIX, ProxyGlobal.random_string(8)]
response = helpers.json_post("/organizations", { :environment => 'sandbox', :parent_id => 'flow', "id" => id }).with_api_key.execute
assert_status(201, response)
assert_equals(response.json['id'], id)

cleanup(helpers)

