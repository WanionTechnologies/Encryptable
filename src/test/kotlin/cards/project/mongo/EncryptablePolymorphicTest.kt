package cards.project.mongo

import cards.project.mongo.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Tests for polymorphic inner Encryptable entities (version 1.0.7 feature)
 *
 * Validates that abstract/interface fields can hold concrete implementations
 * transparently without requiring type annotations or manual configuration.
 */
class EncryptablePolymorphicTest : BaseEncryptableTest() {

    @Autowired
    private lateinit var orderRepository: TestOrderRepository

    @Test
    fun `should save and retrieve order with credit card payment polymorphically`() {
        // Given
        val orderSecret = generateSecret()
        val paymentSecret = generateSecret()

        // Create a TestCreditCardPayment instance
        val creditCard = TestCreditCardPayment().withSecret(paymentSecret).apply {
            amount = 150.00
            currency = "USD"
            cardNumber = "4532-1234-5678-9010"
            cardHolderName = "John Doe"
            expiryDate = "12/2028"
        }

        // Cast to TestPayment for polymorphic assignment
        val order = TestOrder().withSecret(orderSecret).apply {
            orderNumber = "ORD-2026-001"
            customerName = "John Doe"
            totalAmount = 150.00
            payment = creditCard // Polymorphic assignment: TestPayment<*> field = TestCreditCardPayment instance
            status = "PENDING"
        }

        // When
        orderRepository.save(order)
        val retrieved = orderRepository.findBySecretOrNull(orderSecret)

        // Then
        assertNotNull(retrieved)
        assertEquals("ORD-2026-001", retrieved?.orderNumber)
        assertEquals("John Doe", retrieved?.customerName)

        // Verify polymorphic field is preserved
        assertNotNull(retrieved?.payment)
        assertTrue(retrieved?.payment is TestCreditCardPayment, "Payment should be TestCreditCardPayment instance")

        val retrievedCard = retrieved?.payment as TestCreditCardPayment
        assertEquals(150.00, retrievedCard.amount)
        assertEquals("USD", retrievedCard.currency)
        assertEquals("4532-1234-5678-9010", retrievedCard.cardNumber)
        assertEquals("John Doe", retrievedCard.cardHolderName)
        assertEquals("12/2028", retrievedCard.expiryDate)
        assertEquals("CREDIT_CARD", retrievedCard.getPaymentType())

        // Cleanup
        orderRepository.deleteBySecret(orderSecret)
    }

    @Test
    fun `should save and retrieve order with PIX payment polymorphically`() {
        // Given
        val orderSecret = generateSecret()
        val paymentSecret = generateSecret()

        val pixPayment = TestPixPayment().withSecret(paymentSecret).apply {
            amount = 250.50
            currency = "BRL"
            pixKey = "user@example.com"
            pixKeyType = "EMAIL"
        }

        val order = TestOrder().withSecret(orderSecret).apply {
            orderNumber = "ORD-2026-002"
            customerName = "Maria Silva"
            totalAmount = 250.50
            payment = pixPayment // Polymorphic assignment: TestPayment<*> field = TestPixPayment instance
            status = "COMPLETED"
        }

        // When
        orderRepository.save(order)
        val retrieved = orderRepository.findBySecretOrNull(orderSecret)

        // Then
        assertNotNull(retrieved)
        assertEquals("ORD-2026-002", retrieved?.orderNumber)

        // Verify polymorphic field type
        assertNotNull(retrieved?.payment)
        assertTrue(retrieved?.payment is TestPixPayment, "Payment should be TestPixPayment instance")

        val retrievedPix = retrieved?.payment as TestPixPayment
        assertEquals(250.50, retrievedPix.amount)
        assertEquals("BRL", retrievedPix.currency)
        assertEquals("user@example.com", retrievedPix.pixKey)
        assertEquals("EMAIL", retrievedPix.pixKeyType)
        assertEquals("PIX", retrievedPix.getPaymentType())

        // Cleanup
        orderRepository.deleteBySecret(orderSecret)
    }

    @Test
    fun `should save and retrieve order with bank transfer payment polymorphically`() {
        // Given
        val orderSecret = generateSecret()
        val paymentSecret = generateSecret()

        val bankTransfer = TestBankTransferPayment().withSecret(paymentSecret).apply {
            amount = 1000.00
            currency = "EUR"
            bankCode = "DEUTDEFF"
            accountNumber = "DE89370400440532013000"
            routingNumber = "37040044"
        }

        val order = TestOrder().withSecret(orderSecret).apply {
            orderNumber = "ORD-2026-003"
            customerName = "Hans Mueller"
            totalAmount = 1000.00
            payment = bankTransfer // Polymorphic assignment: TestPayment<*> field = TestBankTransferPayment instance
            status = "PROCESSING"
        }

        // When
        orderRepository.save(order)
        val retrieved = orderRepository.findBySecretOrNull(orderSecret)

        // Then
        assertNotNull(retrieved)
        assertEquals("ORD-2026-003", retrieved?.orderNumber)

        // Verify polymorphic field type
        assertNotNull(retrieved?.payment)
        assertTrue(retrieved?.payment is TestBankTransferPayment, "Payment should be TestBankTransferPayment instance")

        val retrievedTransfer = retrieved?.payment as TestBankTransferPayment
        assertEquals(1000.00, retrievedTransfer.amount)
        assertEquals("EUR", retrievedTransfer.currency)
        assertEquals("DEUTDEFF", retrievedTransfer.bankCode)
        assertEquals("DE89370400440532013000", retrievedTransfer.accountNumber)
        assertEquals("37040044", retrievedTransfer.routingNumber)
        assertEquals("BANK_TRANSFER", retrievedTransfer.getPaymentType())

        // Cleanup
        orderRepository.deleteBySecret(orderSecret)
    }

    @Test
    fun `should update polymorphic payment from one type to another`() {
        // Given - Start with credit card payment
        val orderSecret = generateSecret()
        val creditCardSecret = generateSecret()

        val creditCard = TestCreditCardPayment().withSecret(creditCardSecret).apply {
            amount = 100.00
            currency = "USD"
            cardNumber = "4532-1234-5678-9010"
            cardHolderName = "John Doe"
            expiryDate = "12/2028"
        }

        val order = TestOrder().withSecret(orderSecret).apply {
            orderNumber = "ORD-2026-004"
            customerName = "John Doe"
            totalAmount = 100.00
            payment = creditCard
            status = "PENDING"
        }

        orderRepository.save(order)

        // When - Change to PIX payment
        val pixSecret = generateSecret()
        val pixPayment = TestPixPayment().withSecret(pixSecret).apply {
            amount = 100.00
            currency = "BRL"
            pixKey = "john.doe@email.com"
            pixKeyType = "EMAIL"
        }

        val retrieved = orderRepository.findBySecretOrNull(orderSecret)
        retrieved?.payment = pixPayment // Polymorphic reassignment: change payment type

        orderRepository.flushThenClear()

        // Then
        val afterUpdate = orderRepository.findBySecretOrNull(orderSecret)
        assertNotNull(afterUpdate?.payment)
        assertTrue(afterUpdate?.payment is TestPixPayment, "Payment should now be TestPixPayment instance")

        val updatedPix = afterUpdate?.payment as TestPixPayment
        assertEquals("john.doe@email.com", updatedPix.pixKey)
        assertEquals("EMAIL", updatedPix.pixKeyType)
        assertEquals("PIX", updatedPix.getPaymentType())

        // Verify old credit card was deleted (because of @PartOf)
        // Note: Repository check would require TestCreditCardPaymentRepository injection
        // For now, we verify the field was successfully updated

        // Cleanup
        orderRepository.deleteBySecret(orderSecret)
    }

    @Test
    fun `should handle null polymorphic field`() {
        // Given
        val orderSecret = generateSecret()

        val order = TestOrder().withSecret(orderSecret).apply {
            orderNumber = "ORD-2026-005"
            customerName = "Jane Smith"
            totalAmount = 75.00
            payment = null // No payment yet
            status = "AWAITING_PAYMENT"
        }

        // When
        orderRepository.save(order)
        val retrieved = orderRepository.findBySecretOrNull(orderSecret)

        // Then
        assertNotNull(retrieved)
        assertEquals("ORD-2026-005", retrieved?.orderNumber)
        assertNull(retrieved?.payment, "Payment should be null")
        assertEquals("AWAITING_PAYMENT", retrieved?.status)

        // Cleanup
        orderRepository.deleteBySecret(orderSecret)
    }

    @Test
    fun `should lazy load polymorphic field correctly`() {
        // Given
        val orderSecret = generateSecret()
        val paymentSecret = generateSecret()

        val creditCard = TestCreditCardPayment().withSecret(paymentSecret).apply {
            amount = 500.00
            currency = "GBP"
            cardNumber = "5500-0000-0000-0004"
            cardHolderName = "Alice Johnson"
            expiryDate = "06/2027"
        }

        val order = TestOrder().withSecret(orderSecret).apply {
            orderNumber = "ORD-2026-006"
            customerName = "Alice Johnson"
            totalAmount = 500.00
            payment = creditCard
            status = "COMPLETED"
        }

        orderRepository.save(order)

        // When - Clear context to force lazy loading
        orderRepository.flushThenClear()
        val retrieved = orderRepository.findBySecretOrNull(orderSecret)

        // Then - First access should trigger lazy load
        assertNotNull(retrieved)
        val lazyLoadedPayment = retrieved?.payment // Lazy load happens here

        assertNotNull(lazyLoadedPayment)
        assertTrue(lazyLoadedPayment is TestCreditCardPayment, "Lazy loaded payment should be TestCreditCardPayment")

        val card = lazyLoadedPayment as TestCreditCardPayment
        assertEquals("5500-0000-0000-0004", card.cardNumber)
        assertEquals("Alice Johnson", card.cardHolderName)

        // Cleanup
        orderRepository.deleteBySecret(orderSecret)
    }

    @Test
    fun `should handle batch operations with different polymorphic types`() {
        // Given
        val orderSecrets = (1..3).map { generateSecret() }
        val paymentSecrets = (1..3).map { generateSecret() }

        val creditCard = TestCreditCardPayment().withSecret(paymentSecrets[0]).apply {
            amount = 100.00
            currency = "USD"
            cardNumber = "4532-1111-1111-1111"
            cardHolderName = "User One"
            expiryDate = "12/2027"
        }

        val pix = TestPixPayment().withSecret(paymentSecrets[1]).apply {
            amount = 200.00
            currency = "BRL"
            pixKey = "user2@email.com"
            pixKeyType = "EMAIL"
        }

        val bankTransfer = TestBankTransferPayment().withSecret(paymentSecrets[2]).apply {
            amount = 300.00
            currency = "EUR"
            bankCode = "BANK123"
            accountNumber = "ACC123456"
            routingNumber = "ROUTE789"
        }

        val orders = listOf(
            TestOrder().withSecret(orderSecrets[0]).apply {
                orderNumber = "ORD-BATCH-001"
                customerName = "User One"
                totalAmount = 100.00
                payment = creditCard
                status = "COMPLETED"
            },
            TestOrder().withSecret(orderSecrets[1]).apply {
                orderNumber = "ORD-BATCH-002"
                customerName = "User Two"
                totalAmount = 200.00
                payment = pix
                status = "COMPLETED"
            },
            TestOrder().withSecret(orderSecrets[2]).apply {
                orderNumber = "ORD-BATCH-003"
                customerName = "User Three"
                totalAmount = 300.00
                payment = bankTransfer
                status = "COMPLETED"
            }
        )

        // When
        orderRepository.saveAll(orders)
        val retrieved = orderRepository.findBySecrets(orderSecrets)

        // Then
        assertEquals(3, retrieved.size)

        val order1 = retrieved.find { it.orderNumber == "ORD-BATCH-001" }
        assertTrue(order1?.payment is TestCreditCardPayment)

        val order2 = retrieved.find { it.orderNumber == "ORD-BATCH-002" }
        assertTrue(order2?.payment is TestPixPayment)

        val order3 = retrieved.find { it.orderNumber == "ORD-BATCH-003" }
        assertTrue(order3?.payment is TestBankTransferPayment)

        // Cleanup
        orderRepository.deleteBySecrets(orderSecrets)
    }
}
