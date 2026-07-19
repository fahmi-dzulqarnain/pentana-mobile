//
//  PasskeyManager.swift
//  iosApp
//
//  Bridges the WebAuthn ceremony to iOS AuthenticationServices. Parses the
//  server's options JSON, drives ASAuthorizationController, and serialises the
//  result into the WebAuthn credential JSON the server expects. This is the only
//  passkey piece that can't live in the shared KMP module.
//

import AuthenticationServices
import Foundation
import UIKit

final class PasskeyManager: NSObject {
    enum PasskeyError: Error { case badOptions, canceled, failed }

    private let relyingParty: String
    private var continuation: CheckedContinuation<String, Error>?

    init(relyingParty: String) { self.relyingParty = relyingParty }

    /// Discoverable sign-in. Returns the assertion as WebAuthn credential JSON.
    func signIn(optionsJson: String) async throws -> String {
        let opts = try parse(optionsJson)
        guard let challenge = Self.b64urlDecode(opts["challenge"] as? String) else { throw PasskeyError.badOptions }
        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: rpId(opts))
        return try await perform(provider.createCredentialAssertionRequest(challenge: challenge))
    }

    /// Create a passkey on this device. Returns the attestation as WebAuthn credential JSON.
    func register(optionsJson: String) async throws -> String {
        let opts = try parse(optionsJson)
        guard let challenge = Self.b64urlDecode(opts["challenge"] as? String),
              let user = opts["user"] as? [String: Any],
              let userID = Self.b64urlDecode(user["id"] as? String),
              let userName = user["name"] as? String
        else { throw PasskeyError.badOptions }

        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: rpId(opts))
        return try await perform(provider.createCredentialRegistrationRequest(challenge: challenge, name: userName, userID: userID))
    }

    // MARK: - Plumbing

    private func rpId(_ opts: [String: Any]) -> String {
        if let rp = opts["rpId"] as? String { return rp }                       // assertion options
        if let rp = (opts["rp"] as? [String: Any])?["id"] as? String { return rp } // creation options
        return relyingParty
    }

    private func perform(_ request: ASAuthorizationRequest) async throws -> String {
        try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    private func parse(_ json: String) throws -> [String: Any] {
        guard let data = json.data(using: .utf8),
              let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { throw PasskeyError.badOptions }
        return obj
    }

    static func b64urlDecode(_ value: String?) -> Data? {
        guard var str = value else { return nil }
        str = str.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while str.count % 4 != 0 { str += "=" }
        return Data(base64Encoded: str)
    }

    static func b64urlEncode(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private static func stringify(_ obj: [String: Any]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: obj),
              let json = String(data: data, encoding: .utf8) else { return "{}" }
        return json
    }
}

extension PasskeyManager: ASAuthorizationControllerDelegate {
    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        let pendingContinuation = continuation
        continuation = nil
        switch authorization.credential {
        case let reg as ASAuthorizationPlatformPublicKeyCredentialRegistration:
            pendingContinuation?.resume(returning: Self.stringify([
                "id": Self.b64urlEncode(reg.credentialID),
                "rawId": Self.b64urlEncode(reg.credentialID),
                "type": "public-key",
                "response": [
                    "clientDataJSON": Self.b64urlEncode(reg.rawClientDataJSON),
                    "attestationObject": Self.b64urlEncode(reg.rawAttestationObject ?? Data()),
                ],
            ]))
        case let assertion as ASAuthorizationPlatformPublicKeyCredentialAssertion:
            pendingContinuation?.resume(returning: Self.stringify([
                "id": Self.b64urlEncode(assertion.credentialID),
                "rawId": Self.b64urlEncode(assertion.credentialID),
                "type": "public-key",
                "response": [
                    "clientDataJSON": Self.b64urlEncode(assertion.rawClientDataJSON),
                    "authenticatorData": Self.b64urlEncode(assertion.rawAuthenticatorData),
                    "signature": Self.b64urlEncode(assertion.signature),
                    "userHandle": Self.b64urlEncode(assertion.userID),
                ],
            ]))
        default:
            pendingContinuation?.resume(throwing: PasskeyError.failed)
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        let pendingContinuation = continuation
        continuation = nil
        if let authError = error as? ASAuthorizationError, authError.code == .canceled {
            pendingContinuation?.resume(throwing: PasskeyError.canceled)
        } else {
            pendingContinuation?.resume(throwing: error)
        }
    }
}

extension PasskeyManager: ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        return scene?.windows.first(where: { $0.isKeyWindow }) ?? ASPresentationAnchor()
    }
}
