package it.scontrina.receipts.receipt;

import it.scontrina.receipts.receipt.VatCalculator.LineInput;
import it.scontrina.receipts.receipt.VatCalculator.VatCalculation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static it.scontrina.receipts.receipt.VatRate.VAT_10;
import static it.scontrina.receipts.receipt.VatRate.VAT_22;
import static it.scontrina.receipts.receipt.VatRate.VAT_4;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;


class VatCalculatorTest {

    private final VatCalculator calculator = new VatCalculator();

    @Test
    void test_givenSingleLineAt22_whenCalculate_thenVatIsExtractedFromGross() {
        VatCalculation result = calculator.calculate(List.of(line("1", "12.20", VAT_22)));

        assertThat(result.breakdown()).singleElement().satisfies(b -> {
            assertThat(b.getVatRate()).isEqualTo(VAT_22);
            assertThat(b.getGross()).isEqualTo(bd("12.20"));
            assertThat(b.getTaxable()).isEqualTo(bd("10.00"));
            assertThat(b.getVat()).isEqualTo(bd("2.20"));
        });
        assertThat(result.total()).isEqualTo(bd("12.20"));
        assertThat(result.vatTotal()).isEqualTo(bd("2.20"));
    }

    @Test
    void test_givenLinesWithMixedRatesInAnyOrder_whenCalculate_thenBreakdownGroupedByRateInEnumOrder() {
        VatCalculation result = calculator.calculate(List.of(
                line("1", "12.20", VAT_22),
                line("2", "1.10", VAT_10),
                line("1", "5.20", VAT_4)));

        assertThat(result.breakdown())
                .extracting(VatBreakdown::getVatRate, VatBreakdown::getGross, VatBreakdown::getTaxable, VatBreakdown::getVat)
                .containsExactly(
                        tuple(VAT_4, bd("5.20"), bd("5.00"), bd("0.20")),
                        tuple(VAT_10, bd("2.20"), bd("2.00"), bd("0.20")),
                        tuple(VAT_22, bd("12.20"), bd("10.00"), bd("2.20")));
        assertThat(result.total()).isEqualTo(bd("19.60"));
        assertThat(result.vatTotal()).isEqualTo(bd("2.60"));
    }

    @Test
    void test_givenNoLines_whenCalculate_thenTotalsAreZeroWithTwoDecimals() {
        VatCalculation result = calculator.calculate(List.of());

        assertThat(result.lines()).isEmpty();
        assertThat(result.breakdown()).isEmpty();
        assertThat(result.total()).isEqualTo(bd("0.00"));
        assertThat(result.vatTotal()).isEqualTo(bd("0.00"));
    }

    private static LineInput line(String quantity, String unitPrice, VatRate rate) {
        return new LineInput("articolo", bd(quantity), bd(unitPrice), rate);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
