use serde::Serialize;
use stunclient::StunClient;
use std::net::SocketAddr;

#[derive(Debug, Serialize)]
pub struct NatResult {
    pub nat_type: String,
    pub score: f64,
    pub details: String,
    pub mappings: Vec<String>,
}

/// RFC 5780 NAT Behavior Discovery using multiple STUN servers
pub fn detect_nat_type() -> NatResult {
    let servers: Vec<&str> = vec![
        "stun.l.google.com:19302",
        "stun1.l.google.com:19302",
        "stun2.l.google.com:19302",
        "stun.cloudflare.com:3478",
    ];

    let mut mappings: Vec<(String, SocketAddr)> = Vec::new();

    for server_addr in &servers {
        if let Ok(addr) = server_addr.parse::<SocketAddr>() {
            let client = StunClient::new(addr);
            if let Ok(mapped) = client.query_external_address() {
                mappings.push((server_addr.to_string(), mapped));
            }
        }
    }

    let ports: Vec<u16> = mappings.iter().map(|(_, a)| a.port()).collect();
    let unique_ports: std::collections::HashSet<u16> = ports.iter().copied().collect();

    let details = format!(
        "Queried {} servers, unique mapped ports: {}",
        servers.len(),
        unique_ports.len()
    );

    let (nat_type, score) = if mappings.is_empty() {
        ("UDP Blocked".to_string(), 0.2)
    } else if unique_ports.len() == 1 {
        ("Cone NAT (NAT2)".to_string(), 0.8)
    } else {
        ("Symmetric NAT (NAT4)".to_string(), 0.25)
    };

    NatResult {
        nat_type,
        score,
        details,
        mappings: mappings.iter().map(|(s, a)| format!("{} -> {}", s, a)).collect(),
    }
}
