use rand::Rng;
use serde::Serialize;
use std::collections::HashSet;
use std::net::{SocketAddr, UdpSocket};
use std::time::Duration;

#[derive(Debug, Serialize)]
pub struct NatResult {
    pub nat_type: String,
    pub score: f64,
    pub details: String,
    pub mappings: Vec<String>,
}

/// RFC 5780 NAT Behavior Discovery via raw STUN Binding requests
pub fn detect_nat_type() -> NatResult {
    let servers = [
        "stun.l.google.com:19302",
        "stun1.l.google.com:19302",
        "stun2.l.google.com:19302",
        "stun.cloudflare.com:3478",
    ];

    let mut mappings: Vec<(String, SocketAddr)> = Vec::new();
    let socket = UdpSocket::bind("0.0.0.0:0").expect("Failed to bind UDP socket");
    socket.set_read_timeout(Some(Duration::from_secs(3))).ok();

    for server_str in &servers {
        if let Ok(addr) = server_str.parse::<SocketAddr>() {
            if let Some(mapped) = stun_query(&socket, addr) {
                mappings.push((server_str.to_string(), mapped));
            }
        }
    }

    let ports: Vec<u16> = mappings.iter().map(|(_, a)| a.port()).collect();
    let unique_ports: HashSet<u16> = ports.iter().copied().collect();

    let (nat_type, score) = if mappings.is_empty() {
        ("UDP Blocked".to_string(), 0.2)
    } else if unique_ports.len() == 1 {
        ("Cone NAT (EIM/NAT2)".to_string(), 0.8)
    } else {
        ("Symmetric NAT (ADM/NAT4)".to_string(), 0.25)
    };

    NatResult {
        details: format!("Queried {} servers, {} unique ports", servers.len(), unique_ports.len()),
        nat_type,
        score,
        mappings: mappings.iter().map(|(s, a)| format!("{} -> {}", s, a)).collect(),
    }
}

fn stun_query(socket: &UdpSocket, server: SocketAddr) -> Option<SocketAddr> {
    let mut rng = rand::thread_rng();
    let mut req = [0u8; 20];
    req[0] = 0x00; req[1] = 0x01; // Binding Request
    // Magic cookie
    req[4] = 0x21; req[5] = 0x12; req[6] = 0xA4; req[7] = 0x42;
    // Random transaction ID
    for i in 8..20 { req[i] = rng.gen(); }

    socket.send_to(&req, server).ok()?;

    let mut buf = [0u8; 256];
    let (len, _) = socket.recv_from(&mut buf).ok()?;
    if len < 20 { return None; }

    // Parse XOR-MAPPED-ADDRESS (0x0020)
    let mut pos = 20;
    while pos + 4 <= len {
        let attr_type = u16::from_be_bytes([buf[pos], buf[pos + 1]]);
        let attr_len = u16::from_be_bytes([buf[pos + 2], buf[pos + 3]]) as usize;
        pos += 4;
        if attr_type == 0x0020 && attr_len >= 8 {
            let family = buf[pos + 1];
            let xport = u16::from_be_bytes([buf[pos + 2], buf[pos + 3]]) ^ 0x2112;
            if family == 0x01 {
                let octets = [buf[pos + 4], buf[pos + 5], buf[pos + 6], buf[pos + 7]];
                let ip = std::net::Ipv4Addr::new(
                    octets[0] ^ 0x21, octets[1] ^ 0x12, octets[2] ^ 0xA4, octets[3] ^ 0x42
                );
                return Some(SocketAddr::new(std::net::IpAddr::V4(ip), xport));
            }
        }
        pos += attr_len;
        if attr_len % 4 != 0 { pos += 4 - (attr_len % 4); }
    }
    None
}
