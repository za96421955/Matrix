package main

import (
	"encoding/json"
	"fmt"
	"os"
)

func main() {
	var data map[string]interface{}
	json.Unmarshal([]byte(os.Args[1]), &data)
	fmt.Println(data["print"])
}
