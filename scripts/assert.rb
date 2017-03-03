def assert_equals(expected, actual)
  if expected != actual
    raise "expected[%s] actual[%s]" % [expected, actual]
  end
end

def assert_nil(value)
  if !value.nil?
    raise "expected nil but got[%s]" % value
  end
end

def assert_envelope(response)
  tests = [
    200 == response.status,
    response.json.has_key?("status"),
    response.json.has_key?("headers"),
    response.json.has_key?("body")
  ]

  if !tests.all? { |r| r }
    raise "expected response envelope for %s %s but got\n  HTTP %s\n%s" %
          [response.request_method, response.request_uri, response.status, response.json_stack_trace]
  end
end

def assert_generic_error(response, message)
  assert_equals(response.status, 422)
  assert_equals(response.json['code'], "generic_error")
  assert_equals(response.json['messages'], [message])
end

def assert_status(expected, response)
  if expected != response.status
    msg = "\n\nInvalid HTTP Status Code: expected[%s] actual[%s]\n" % [expected, response.status]
    msg << response.json_stack_trace
    msg << "\n\n"
    raise msg
  end
end

def assert_statuses(expected, response)
  if !expected.include?(response.status)
    msg = "\n\nInvalid HTTP Status Code: expected one of[%s] actual[%s]\n" % [expected.join(" "), response.status]
    msg << response.json_stack_trace
    msg << "\n\n"
    raise msg
  end
end

def assert_unauthorized(response)
  assert_status(401, response)
end

