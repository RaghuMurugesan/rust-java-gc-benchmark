use axum::{
    routing::get,
    Router,
    response::IntoResponse,
};
use prometheus::{Encoder, TextEncoder, HistogramVec, HistogramOpts, register_histogram_vec};
use lazy_static::lazy_static;
use std::time::Instant;
use std::env;

lazy_static! {
    static ref HTTP_REQUEST_DURATION: HistogramVec = register_histogram_vec!(
        HistogramOpts::new(
            "http_request_duration_seconds",
            "HTTP request duration in seconds"
        ).buckets(vec![0.080, 0.085, 0.090, 0.095, 0.100, 0.110, 0.120, 0.150, 0.200, 0.300, 0.500, 1.0]),
        &[]
    ).unwrap();

    static ref HTTP_CLIENT: reqwest::Client = reqwest::Client::new();
}

#[tokio::main]
async fn main() {
    let port = env::var("PORT").unwrap_or_else(|_| "8080".to_string());
    let addr = format!("0.0.0.0:{}", port);

    let app = Router::new()
        .route("/", get(handle_request))
        .route("/metrics", get(metrics))
        .route("/health", get(health));

    println!("Rust service listening on {}", addr);

    let listener = tokio::net::TcpListener::bind(&addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}

async fn handle_request() -> impl IntoResponse {
    let start = Instant::now();

    // Step 1: Allocate 1MB of data (simulates request parsing)
    let mut allocations: Vec<Vec<u8>> = Vec::with_capacity(1000);
    for _ in 0..1000 {
        allocations.push(vec![0u8; 1024]); // 1KB each = 1MB total
    }

    // Step 2: Touch all allocations (prevents optimization, simulates validation)
    let mut checksum: i64 = 0;
    for arr in &allocations {
        for i in (0..arr.len()).step_by(64) {
            checksum += arr[i] as i64;
        }
    }

    // Step 3: Call downstream backend
    let backend_url = env::var("BACKEND_URL").unwrap_or_else(|_| "http://localhost:8080/".to_string());
    let _response = HTTP_CLIENT.get(&backend_url).send().await;

    // Record latency
    let duration = start.elapsed().as_secs_f64();
    HTTP_REQUEST_DURATION.with_label_values(&[]).observe(duration);

    // Drop allocations automatically when this function returns (Rust ownership)

    format!("ok (checksum: {})", checksum)
}

async fn metrics() -> impl IntoResponse {
    let encoder = TextEncoder::new();
    let metric_families = prometheus::gather();
    let mut buffer = Vec::new();
    encoder.encode(&metric_families, &mut buffer).unwrap();

    (
        [("content-type", "text/plain; version=0.0.4")],
        String::from_utf8(buffer).unwrap()
    )
}

async fn health() -> &'static str {
    "ok"
}
