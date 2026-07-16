// cargo-deps: serde_json
use serde_json::Value;
fn main() {
    let args: Vec<String> = std::env::args().collect();
    let v: Value = serde_json::from_str(&args[1]).unwrap();
    println!("{}", v["print"].as_str().unwrap());
}