def assert_equals(actual, expected)
  if expected != actual
    raise "expected[%s] actual[%s]" % [expected, actual]
  end
end

def assert_nil(value)
  if !value.nil?
    raise "expected nil but got[%s]" % value
  end
end

def assert_not_nil(value)
  if value.nil?
    raise "expected value but got nil"
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

def assert_jsonp(response, expected_callback_name)
  prefix = '/**/'
  if response.body.start_with?(prefix)
    stripped = response.body[prefix.length, response.body.length].strip
    if md = stripped.match(/^(.+)\((.*)\)$/m)
      callback = md[1]
      body = ProxyGlobal.parse_json(md[2])

      tests = [
        200 == response.status,
        callback == expected_callback_name,
        body.has_key?("status"),
        body.has_key?("headers"),
        body.has_key?("body")
      ]

      if tests.all? { |r| r }
        return
      end

    end
  end

  raise "expected response envelope with jsonp callback[%s] for %s %s but got\n  HTTP %s\n%s" %
        [expected_callback_name, response.request_method, response.request_uri, response.status, response.json_stack_trace]
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

