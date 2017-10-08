require 'time'

module ProxyGlobal

  LOG_FILE = "/tmp/proxy.test.log"
  
  @@proxy_request_tmp_index = 1

  def ProxyGlobal.info(message)
    ProxyGlobal.log("info", message)
  end
    
  def ProxyGlobal.log(level, message)
    File.open(LOG_FILE, "a") do |out|
      out << "%s [%s] %s\n" % [level, Time.new.to_s, message]
    end
  end

  def ProxyGlobal.tmp_file_path
    path = "/tmp/proxy.test.%s.%s.tmp" % [Process.pid, @@proxy_request_tmp_index]
    @@proxy_request_tmp_index += 1
    path
  end

  def ProxyGlobal.format_json(hash, opts = {})
    indent = opts.delete(:indent).to_i
    indent_string = " " * indent
    JSON.pretty_generate(hash).gsub(/\[\s+\]/, '[]').gsub(/\{\s+\}/, '{}').split("\n").map { |l| "%s%s" % [indent_string, l] }.join("\n")
  end

  def ProxyGlobal.random_string(length=36)
    rand(36**length).to_s(36)
  end

  def ProxyGlobal.parse_json(body)
    begin
      JSON.parse(body)
    rescue TypeError => e
      raise "body is not a string: #{e}"
    rescue JSON::JSONError => e
      base = "ERROR parsing json"
      if body.strip.empty?
        raise "%s: body was empty\n" % base
      else
        msg = "%s: Invalid JSON\n" % base
        msg << body
        msg << "\n"
        raise msg
      end
    end
  end
  
end

class Response

  attr_reader :request_method, :request_uri, :status, :body

  def initialize(request_method, request_uri, status, body)
    @request_method = request_method
    @request_uri = request_uri
    @status = status
    @body = body
  end

  def unwrap_envelope
    js = json
    Response.new(@request_method, @request_uri, js['status'], ProxyGlobal.format_json(js['body']))
  end

  def unwrap_jsonp
    prefix = '/**/'
    if @body.start_with?(prefix)
      stripped = @body[prefix.length, @body.length].strip
      if md = stripped.match(/^(.+)\((.*)\)$/m)
        callback = md[1]
        js = ProxyGlobal.parse_json(md[2])
        return Response.new(@request_method, @request_uri, js['status'], ProxyGlobal.format_json(js['body']))
      end
    end
    raise "Cannot unwrap json from: " + @body
  end
  
  def json
    ProxyGlobal.parse_json(@body)
  end

  def json_stack_trace
    msg = ""
    Helpers.with_tmp_file(@body) do |tmp|
      lines = IO.read(tmp).split("\n")

      printed = false
      if lines.size < 15
        # Print whole body if json
        begin
          msg += ProxyGlobal.format_json(JSON.parse(@body), :indent => 2)
          printed = true
        rescue JSON::JSONError => e
        end
      end

      if !printed
        lines[0, 10].each do |l|
          msg += "  %s" % l
        end
      end
    end
    msg
  end
  
end

class Helpers

  def initialize(base_url, api_key_file)
    @base_url = base_url
    @api_key_file = api_key_file
  end

  def get(url)
    new_request("GET", url)
  end

  def delete(url)
    new_request("DELETE", url)
  end

  def json_put(url, hash = nil)
    json_request("PUT", url, hash).with_content_type("application/json")

  end

  def json_post(url, hash = nil)
    json_request("POST", url, hash).with_content_type("application/json")
  end

  def json_request(method, url, hash)
    r = new_request(method, url)
    if hash
      r.with_body(ProxyGlobal.format_json(hash))
    else
      r
    end
  end

  def new_request(method, url)
    Request.new(method, "%s%s" % [@base_url, url], @api_key_file)
  end

end

class Request

  def initialize(method, url, api_key_path)
    @method = method
    @url = url
    @token = nil
    @path = nil
    @api_key = false
    @headers = []

    if !File.exists?(api_key_path)
      raise "ERROR: File[#{api_key_path}] does not exist"
    end
    @api_key_path = api_key_path
  end

  def with_api_key
    @api_key = true
    self
  end

  def with_content_type(ct)
    with_header("Content-type", ct)
    self
  end

  def with_authorization_header(value)
    with_header("Authorization", value)
  end

  def with_header(name, value)
    @headers << [name, value]
    self
  end

  def with_body(body)
    Helpers.with_tmp_file(body) do |tmp|
      with_file(tmp)
    end
  end

  def with_file(path)
    if !File.exists?(path)
      raise "ERROR: File[#{path}] does not exist"
    end

    @path = path
    self
  end
  
  #curl("-X #{method} -d@#{path} -H 'Content-type: application/json' #{@base_url}#{url}")
  def execute
    params = ["curl --silent -w 'status[%{http_code}]'"]

    if @method.upcase != "GET"
      params << "-X %s" % @method
    end

    @headers.each do |pair|
      params << "-H '%s: %s'" % [pair[0], pair[1]]
    end
    
    if @api_key
      params << "-u `cat %s`:" % @api_key_path
    end

    if @path
      params << "-d@%s" % @path
    end

    params << "'%s'" % @url
    
    cmd = params.join(" ")
    ProxyGlobal.info(cmd)
    puts cmd.gsub(/\s+(\-+)/, " \\\n    \\1")
    puts ""

    tmpfile = ProxyGlobal.tmp_file_path
    if system(cmd + " > #{tmpfile}")
      results = IO.read(tmpfile).strip
      if md = results.match(/status\[(\d+)\]/)
        status = md[1].to_i
        body = results.sub(/\s*status\[(\d+)\]\s*/, '')

        r = Response.new(@method, @url, status, body)
        if r.status >= 500 || r.status < 200
          msg =[]
          msg << "ERROR: HTTP %s for %s %s" % [r.status, @method, @url]
          msg << ""

          begin
            js = JSON.parse(body)
            if js["code"] && js["messages"]
              msg << " - Code: %s" % js['code']
              msg << "   %s" % js['messages'].join("\n   ")
            else
              msg << r.json_stack_trace
            end
          rescue
            msg << r.json_stack_trace
          end

          raise msg.join("\n")
        end
        r
      else
        raise "ERROR: Could not parse HTTP Status code for %s %s" % [@method, @url]
      end        
    else
      raise "ERROR: curl failed for %s" % cmd
    end
  end  

  def Helpers.with_tmp_file(contents, opts={})
    delete = opts.delete(:delete)
    path = ProxyGlobal.tmp_file_path
    
    File.open(path, "w") do |out|
      out << contents
    end

    result = yield path

    if delete
      File.delete(path)
    end

    result
  end
end
