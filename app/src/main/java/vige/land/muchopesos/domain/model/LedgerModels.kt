package vige.land.muchopesos.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes [BigDecimal] using [BigDecimal.toPlainString] so local caches and snapshots
 * preserve exact decimal precision with no scientific notation or floating-point drift.
 */
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("vige.land.muchopesos.BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder.encodeString(value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
}

/**
 * Serializes [LocalDate] as canonical ISO-8601 `YYYY-MM-DD`.
 */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("vige.land.muchopesos.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

@Serializable
enum class PartnerRole {
    PARTNER_A,
    PARTNER_B;

    fun other(): PartnerRole = when (this) {
        PARTNER_A -> PARTNER_B
        PARTNER_B -> PARTNER_A
    }
}

@Serializable
enum class RecordType {
    EXPENSE,
    SETTLEMENT,
    VOID,
}

@Serializable
enum class SplitType {
    EQUAL,
    EXACT,
    PERCENTAGE,
    FULL,
    SETTLEMENT,
}

@Serializable
data class CurrencySpec(
    val code: String,
    val symbol: String,
    val name: String,
    val fractionDigits: Int = 2,
)

object CurrencyCatalog {
    val USD = CurrencySpec(code = "USD", symbol = "$", name = "US Dollar", fractionDigits = 2)
    val EUR = CurrencySpec(code = "EUR", symbol = "€", name = "Euro", fractionDigits = 2)
    val NOK = CurrencySpec(code = "NOK", symbol = "kr", name = "Norwegian Krone", fractionDigits = 2)
    val GBP = CurrencySpec(code = "GBP", symbol = "£", name = "British Pound", fractionDigits = 2)
    val CAD = CurrencySpec(code = "CAD", symbol = "CA$", name = "Canadian Dollar", fractionDigits = 2)
    val AUD = CurrencySpec(code = "AUD", symbol = "A$", name = "Australian Dollar", fractionDigits = 2)
    val SEK = CurrencySpec(code = "SEK", symbol = "kr", name = "Swedish Krona", fractionDigits = 2)
    val DKK = CurrencySpec(code = "DKK", symbol = "kr", name = "Danish Krone", fractionDigits = 2)
    val CHF = CurrencySpec(code = "CHF", symbol = "CHF", name = "Swiss Franc", fractionDigits = 2)
    val JPY = CurrencySpec(code = "JPY", symbol = "¥", name = "Japanese Yen", fractionDigits = 0)
    val MXN = CurrencySpec(code = "MXN", symbol = "MX$", name = "Mexican Peso", fractionDigits = 2)
    val NZD = CurrencySpec(code = "NZD", symbol = "NZ$", name = "New Zealand Dollar", fractionDigits = 2)
    val PLN = CurrencySpec(code = "PLN", symbol = "zł", name = "Polish Złoty", fractionDigits = 2)
    val BRL = CurrencySpec(code = "BRL", symbol = "R$", name = "Brazilian Real", fractionDigits = 2)
    val INR = CurrencySpec(code = "INR", symbol = "₹", name = "Indian Rupee", fractionDigits = 2)
    val KRW = CurrencySpec(code = "KRW", symbol = "₩", name = "South Korean Won", fractionDigits = 0)

    val default: CurrencySpec = USD

    val all: List<CurrencySpec> = listOf(
        USD,
        EUR,
        NOK,
        GBP,
        CAD,
        AUD,
        SEK,
        DKK,
        CHF,
        JPY,
        MXN,
        NZD,
        PLN,
        BRL,
        INR,
        KRW,
    )

    private val byCodeMap: Map<String, CurrencySpec> = all.associateBy { it.code }

    fun findByCode(code: String?): CurrencySpec? {
        val normalized = code?.trim()?.uppercase().orEmpty()
        if (normalized.isEmpty()) return null
        return byCodeMap[normalized]
    }

    fun resolve(code: String?): CurrencySpec {
        val normalized = code?.trim()?.uppercase().orEmpty()
        if (normalized.isEmpty()) return default
        return byCodeMap[normalized] ?: CurrencySpec(
            code = normalized,
            symbol = normalized,
            name = normalized,
            fractionDigits = 2,
        )
    }
}

@Serializable
data class LedgerConfig(
    val spreadsheetId: String,
    val ledgerName: String,
    val homeCurrencyCode: String,
    val partnerAEmail: String,
    val partnerAName: String,
    val partnerBEmail: String,
    val partnerBName: String,
    val createdAt: String,
) {
    val currencySpec: CurrencySpec
        get() = CurrencyCatalog.resolve(homeCurrencyCode)

    fun roleForEmail(email: String): PartnerRole {
        val normalized = email.trim()
        if (normalized.isEmpty()) return PartnerRole.PARTNER_A
        return when {
            normalized.equals(partnerAEmail.trim(), ignoreCase = true) -> PartnerRole.PARTNER_A
            normalized.equals(partnerBEmail.trim(), ignoreCase = true) -> PartnerRole.PARTNER_B
            else -> PartnerRole.PARTNER_A
        }
    }

    fun displayNameFor(role: PartnerRole): String = when (role) {
        PartnerRole.PARTNER_A -> partnerAName.ifBlank { partnerAEmail.ifBlank { "Partner A" } }
        PartnerRole.PARTNER_B -> partnerBName.ifBlank { partnerBEmail.ifBlank { "Partner B" } }
    }

    fun emailFor(role: PartnerRole): String = when (role) {
        PartnerRole.PARTNER_A -> partnerAEmail
        PartnerRole.PARTNER_B -> partnerBEmail
    }
}

@Serializable
data class LedgerRow(
    val txId: String,
    val recordType: RecordType,
    val refTxId: String = "",
    val date: String, // YYYY-MM-DD
    val createdAt: String, // ISO-8601 UTC
    val createdBy: String,
    val description: String,
    val categoryGroup: String,
    val category: String,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val paidBy: PartnerRole,
    val splitType: SplitType,
    @Serializable(with = BigDecimalSerializer::class)
    val partnerAShare: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val partnerBShare: BigDecimal,
    val splitMeta: String = "",
    val notes: String = "",
)

@Serializable
data class ActiveTransaction(
    val txId: String,
    val recordType: RecordType,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val createdAt: String,
    val createdBy: String,
    val description: String,
    val category: ExpenseCategory,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val paidBy: PartnerRole,
    val splitType: SplitType,
    @Serializable(with = BigDecimalSerializer::class)
    val partnerAShare: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val partnerBShare: BigDecimal,
    val splitMeta: String = "",
    val notes: String = "",
) {
    val isSettlement: Boolean
        get() = recordType == RecordType.SETTLEMENT

    fun shareFor(role: PartnerRole): BigDecimal = when (role) {
        PartnerRole.PARTNER_A -> partnerAShare
        PartnerRole.PARTNER_B -> partnerBShare
    }
}

@Serializable
data class NetBalanceSummary(
    @Serializable(with = BigDecimalSerializer::class)
    val netForPartnerA: BigDecimal,
    val debtorRole: PartnerRole?,
    val creditorRole: PartnerRole?,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val isSettledUp: Boolean,
) {
    /**
     * Positive means the other partner owes [viewerRole];
     * negative means [viewerRole] owes the other partner.
     */
    fun netFor(viewerRole: PartnerRole): BigDecimal = when (viewerRole) {
        PartnerRole.PARTNER_A -> netForPartnerA
        PartnerRole.PARTNER_B -> if (netForPartnerA.compareTo(BigDecimal.ZERO) == 0) {
            netForPartnerA
        } else {
            netForPartnerA.negate()
        }
    }
}

@Serializable
data class TransactionImpact(
    @Serializable(with = BigDecimalSerializer::class)
    val netForViewer: BigDecimal,
    val isPayer: Boolean,
    val isSettlement: Boolean,
)

@Serializable
data class UserSession(
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val isDemoMode: Boolean = false,
)
