package main

import (
	"fmt"
	"net/http"
	"time"
)

func handler(w http.ResponseWriter, r *http.Request) {
	time.Sleep(78 * time.Millisecond)
	fmt.Fprint(w, "ok")
}

func main() {
	http.HandleFunc("/", handler)
	fmt.Println("Mock backend listening on :8080")
	http.ListenAndServe(":8080", nil)
}
