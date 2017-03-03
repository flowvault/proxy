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

def assert_generic_error(response, message)
  assert_equals(response.status, 422)
  assert_equals(response.json['code'], "generic_error")
  assert_equals(response.json['messages'], [message])
end

def assert_status(response, expected)
  if expected != response.status
    msg = "\n\nInvalid HTTP Status Code: expected[%s] actual[%s]\n" % [expected, response.status]
    msg << response.json_stack_trace
    msg << "\n\n"
    raise msg
  end
end

def assert_unauthorized(response)
  assert_status(response, 401)
end
