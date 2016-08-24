package main

import (
	"github.com/flowcommerce/tools/executor"
)

func main() {
	executor := executor.Create("proxy")

	executor = executor.Add("dev tag")
	executor = executor.Add("dev build_docker_image")
	
	executor.Run()
}
