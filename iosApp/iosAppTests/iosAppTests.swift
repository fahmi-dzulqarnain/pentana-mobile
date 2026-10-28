//
//  iosAppTests.swift
//  iosAppTests
//
//  Created by Fahmi Dzulqarnain on 25/06/2026.
//

import Foundation
import Testing
@testable import iosApp

struct iosAppTests {

    @Test func pushTicketIdParsing() {
        #expect(PushRegistrar.ticketId(from: ["ticket_id": 42]) == 42)
        #expect(PushRegistrar.ticketId(from: ["ticket_id": Int64(7)]) == 7)
        #expect(PushRegistrar.ticketId(from: ["ticket_id": NSNumber(value: 9)]) == 9)
        #expect(PushRegistrar.ticketId(from: ["ticket_id": "15"]) == 15)
        #expect(PushRegistrar.ticketId(from: ["ticket_id": "abc"]) == nil)
        #expect(PushRegistrar.ticketId(from: ["ticket_id": true]) == nil)
        #expect(PushRegistrar.ticketId(from: ["ticket_id": NSNumber(value: true)]) == nil)
        #expect(PushRegistrar.ticketId(from: ["aps": ["alert": "hi"]]) == nil)
        #expect(PushRegistrar.ticketId(from: [:]) == nil)
    }

    @Test func example() async throws {
        // Write your test here and use APIs like `#expect(...)` to check expected conditions.
        // Swift Testing Documentation
        // https://developer.apple.com/documentation/testing
    }

}
