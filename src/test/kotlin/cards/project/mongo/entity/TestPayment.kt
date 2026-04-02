package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.CID
import tech.wanion.encryptable.mongo.*

/**
 * Base class for polymorphic payment entities
 * Note: Open (not abstract) to allow MongoDB instantiation
 */
abstract class TestPayment<P: TestPayment<P>> : Encryptable<P>() {
    abstract override var id: CID?

    @Encrypt
    var amount: Double? = null

    @Encrypt
    var currency: String? = null

    abstract fun getPaymentType(): String
}

/**
 * Credit card payment implementation
 */
@Document(collection = "test_credit_card_payments")
class TestCreditCardPayment : TestPayment<TestCreditCardPayment>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var cardNumber: String? = null

    @Encrypt
    var cardHolderName: String? = null

    @Encrypt
    var expiryDate: String? = null

    override fun getPaymentType(): String = "CREDIT_CARD"
}

/**
 * PIX payment implementation (Brazilian instant payment)
 */
@Document(collection = "test_pix_payments")
class TestPixPayment : TestPayment<TestPixPayment>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var pixKey: String? = null

    @Encrypt
    var pixKeyType: String? = null // EMAIL, CPF, PHONE, RANDOM

    override fun getPaymentType(): String = "PIX"
}

/**
 * Bank transfer payment implementation
 */
@Document(collection = "test_bank_transfer_payments")
class TestBankTransferPayment : TestPayment<TestBankTransferPayment>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var bankCode: String? = null

    @Encrypt
    var accountNumber: String? = null

    @Encrypt
    var routingNumber: String? = null

    override fun getPaymentType(): String = "BANK_TRANSFER"
}

@Repository
interface TestCreditCardPaymentRepository : EncryptableMongoRepository<TestCreditCardPayment>

@Repository
interface TestPixPaymentRepository : EncryptableMongoRepository<TestPixPayment>

@Repository
interface TestBankTransferPaymentRepository : EncryptableMongoRepository<TestBankTransferPayment>

