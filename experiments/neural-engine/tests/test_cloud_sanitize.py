"""Tests for cloud.sanitize (design doc §2.2 outbound sanitization)."""

from __future__ import annotations

from datetime import date

import pytest

from cloud.sanitize import (
    OutboundBlockedError,
    amount_bucket_code,
    sanitize_text,
    scan_pii,
    set_alert_hook,
    verify_outbound,
)


class TestPiiRemoval:
    """Given payloads with PII, sanitize must redact every identity channel."""

    def test_mobile_phone_redacted(self) -> None:
        out = sanitize_text("联系人手机号13812345678，请回电")
        assert "13812345678" not in out
        assert "[PHONE]" in out

    def test_id_card_redacted(self) -> None:
        out = sanitize_text("身份证号110101199003070077已核验")
        assert "110101199003070077" not in out
        assert "[ID_CARD]" in out

    def test_bank_card_redacted(self) -> None:
        out = sanitize_text("还款卡号6222021234567890123")
        assert "6222021234567890123" not in out
        assert "[BANK_CARD]" in out

    def test_id_card_not_double_eaten_by_bank_card_rule(self) -> None:
        # 18-digit ID also matches the 16-19 digit bank-card pattern; the ID
        # rule must win so the tail digits never leak.
        out = sanitize_text("证件110101199003070077")
        assert "[BANK_CARD]" not in out
        assert "007" not in out.replace("[ID_CARD]", "")

    def test_labeled_chinese_name_redacted(self) -> None:
        out = sanitize_text("姓名：张伟，性别男")
        assert "张伟" not in out
        assert "姓名：[NAME]" in out

    def test_applicant_name_redacted(self) -> None:
        out = sanitize_text("申请人为李娜。")
        assert "李娜" not in out

    def test_honorific_name_redacted(self) -> None:
        out = sanitize_text("王先生提交了材料")
        assert "王先生" not in out
        assert "[NAME]" in out

    def test_precise_address_redacted(self) -> None:
        out = sanitize_text("住址北京市朝阳区建国路88号")
        assert "建国路88号" not in out
        assert "[ADDRESS]" in out

    def test_precise_coordinates_generalized_to_city_level(self) -> None:
        out = sanitize_text("定位(39.904200, 116.407400)")
        assert "39.904200" not in out
        assert "<GEO_CITY>" in out

    def test_ner_hook_redacts_custom_spans(self) -> None:
        text = "内部代号蓝鲸项目已上线"

        def hook(t: str) -> list[tuple[int, int, str]]:
            start = t.index("蓝鲸项目")
            return [(start, start + 4, "PROJECT")]

        out = sanitize_text(text, ner_hook=hook)
        assert "蓝鲸项目" not in out
        assert "[PII]" in out


class TestNumericGeneralization:
    """Amounts become bucket codes; dates become relative days."""

    def test_amount_bucket_boundaries(self) -> None:
        assert amount_bucket_code(0.5) == "0-1W"
        assert amount_bucket_code(3.5) == "1W-5W"
        assert amount_bucket_code(7) == "5W-10W"
        assert amount_bucket_code(12.5) == "10W-50W"
        assert amount_bucket_code(75) == "50W-100W"
        assert amount_bucket_code(300) == "100W-500W"
        assert amount_bucket_code(900) == "500W+"

    def test_amount_wan_yuan(self) -> None:
        out = sanitize_text("申请金额12万元")
        assert "12万" not in out
        assert "<AMT:10W-50W>" in out

    def test_amount_plain_yuan(self) -> None:
        out = sanitize_text("尾款8000元")
        assert "<AMT:0-1W>" in out

    def test_amount_currency_symbol_with_commas(self) -> None:
        out = sanitize_text("余额¥35,000")
        assert "35,000" not in out
        assert "<AMT:1W-5W>" in out

    def test_amount_decimal_wan(self) -> None:
        out = sanitize_text("敞口12.5万")
        assert "<AMT:10W-50W>" in out

    def test_iso_date_to_relative_days(self) -> None:
        out = sanitize_text("放款日2024-03-05", reference_date=date(2025, 3, 5))
        assert "2024-03-05" not in out
        assert "D-365" in out

    def test_chinese_date_to_relative_days(self) -> None:
        out = sanitize_text("签约于2024年3月5日", reference_date=date(2024, 4, 4))
        assert "D-30" in out

    def test_invalid_date_left_untouched(self) -> None:
        out = sanitize_text("编号2024-13-40", reference_date=date(2025, 1, 1))
        assert "2024-13-40" in out


class TestStructurePreservation:
    """Statistical profiles must survive — the cloud needs shape, not identity."""

    def test_statistics_and_ratios_preserved(self) -> None:
        text = (
            "bad_rate=0.032; lift=1.45; coverage=0.12; n=100000; "
            "feature flow_cv_30d; 近30天逾期率上升; psi=0.08"
        )
        assert sanitize_text(text) == text

    def test_plain_numbers_without_units_preserved(self) -> None:
        text = "count=5000 mean=123.45"
        assert sanitize_text(text) == text

    def test_label_without_separator_not_treated_as_name(self) -> None:
        # "客户经理" / "申请人要求…" must not trigger name redaction.
        text = "客户经理反馈：申请人要求提高额度"
        assert sanitize_text(text) == text


class TestNumericFalsePositives:
    """Long digit runs in numeric/identifier contexts are NOT card numbers."""

    def test_long_decimal_p_value_untouched(self) -> None:
        text = '"p_value": 0.0000000000000001'
        assert sanitize_text(text) == text

    def test_decimal_looking_like_card_untouched(self) -> None:
        text = '"correlation": 0.6222021234567890'
        assert sanitize_text(text) == text

    def test_decimal_looking_like_id_untouched(self) -> None:
        text = '"rho": 0.110101199003070077'
        assert sanitize_text(text) == text

    def test_exponent_context_untouched(self) -> None:
        text = "thr=2.0000000000000001e-05"
        assert sanitize_text(text) == text

    def test_identifier_context_untouched(self) -> None:
        text = "hash=ab1234567890123456cd"
        assert sanitize_text(text) == text

    def test_percent_context_untouched(self) -> None:
        text = "ratio=1234567890123456%"
        assert sanitize_text(text) == text

    def test_json_kv_context_safe(self) -> None:
        text = '{"p_value": 0.0000000000000001, "rho": 0.6222021234567890}'
        assert sanitize_text(text) == text

    def test_standalone_card_still_redacted(self) -> None:
        out = sanitize_text("6222021234567890123")
        assert out == "[BANK_CARD]"

    def test_card_after_chinese_label_still_redacted(self) -> None:
        out = sanitize_text("还款卡号6222021234567890123")
        assert "[BANK_CARD]" in out

    def test_standalone_id_still_redacted(self) -> None:
        assert sanitize_text("110101199003070077") == "[ID_CARD]"


class TestOutboundVerification:
    """Residual PII after sanitization must block the outbound payload."""

    def test_verify_passes_clean_text(self) -> None:
        verify_outbound("bad_rate=0.032, [NAME], [PHONE]")

    def test_verify_blocks_residual_phone(self) -> None:
        with pytest.raises(OutboundBlockedError):
            verify_outbound("手机号13812345678")

    def test_verify_blocks_residual_id(self) -> None:
        with pytest.raises(OutboundBlockedError):
            verify_outbound("证件110101199003070077")

    def test_alert_hook_fires_on_block(self) -> None:
        hits_seen: list[list] = []
        with pytest.raises(OutboundBlockedError):
            verify_outbound("卡号6222021234567890123", alert_hook=hits_seen.append)
        assert len(hits_seen) == 1
        assert hits_seen[0][0].kind == "bank_card"

    def test_module_level_alert_hook(self) -> None:
        hits_seen: list[list] = []
        set_alert_hook(hits_seen.append)
        try:
            with pytest.raises(OutboundBlockedError):
                verify_outbound("电话13912345678")
            assert hits_seen
        finally:
            set_alert_hook(None)

    def test_scan_reports_kinds(self) -> None:
        kinds = {h.kind for h in scan_pii("姓名：张伟 手机13812345678")}
        assert kinds == {"name", "phone"}
