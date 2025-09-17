package io.github.eroshenkoam.allure.textmarkup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.qameta.allure.ee.client.dto.textmarkup.TextMarkupDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MarkdownToJsonConverterTestCase {

    @Test
    void shouldConvertInternalBoldInItalic() throws JsonProcessingException {
        final String inputString = "* на карточке Договора, в блоке Финансовых атрибутов выведено \"Кредитный лимит\"  и значение из поля thresholds.thresholdBreak\n\nНапример:\n\n\n\n_В ответе функции OAPI/v1/customers/{customerId}/credit: isCustomerUseCreditXL = __false__ и isCreditCustomer = __true__\nВ ответе функции OAPI/v1/customers/{customerId}?customerDatabaseId: isCustomerUseCreditXL = __false__ и isCreditCustomer = __true___";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        assertThat(result).isNotNull();

        final ObjectMapper jsonMapper = new JsonMapper();

        final String expectedJson = "{\"type\": \"doc\", \"content\": [{\"type\": \"paragraph\", \"attrs\": null, \"content\": [{\"text\": \"- на карточке Договора, в блоке Финансовых атрибутов выведено \\\"Кредитный лимит\\\"  и значение из поля thresholds.thresholdBreak\", \"type\": \"text\", \"marks\": null}]}, {\"type\": \"paragraph\", \"attrs\": null, \"content\": [{\"text\": \"Например:\", \"type\": \"text\", \"marks\": null}]}, {\"type\": \"paragraph\", \"attrs\": null, \"content\": [{\"text\": \"В ответе функции OAPI/v1/customers/{customerId}/credit: isCustomerUseCreditXL =\", \"type\": \"text\", \"marks\": [{\"type\": \"italic\"}]}, {\"text\": \" false \", \"type\": \"text\", \"marks\": [{\"type\": \"bold\"}, {\"type\": \"italic\"}]}, {\"text\": \"и isCreditCustomer =\", \"type\": \"text\", \"marks\": [{\"type\": \"italic\"}]}, {\"text\": \" true \", \"type\": \"text\", \"marks\": [{\"type\": \"bold\"}, {\"type\": \"italic\"}]}]}, {\"type\": \"paragraph\", \"attrs\": null, \"content\": [{\"text\": \"В ответе функции OAPI/v1/customers/{customerId}?customerDatabaseId: isCustomerUseCreditXL =\", \"type\": \"text\", \"marks\": [{\"type\": \"italic\"}]}, {\"text\": \" false \", \"type\": \"text\", \"marks\": [{\"type\": \"bold\"}, {\"type\": \"italic\"}]}, {\"text\": \"и isCreditCustomer =\", \"type\": \"text\", \"marks\": [{\"type\": \"italic\"}]}, {\"text\": \" true \", \"type\": \"text\", \"marks\": [{\"type\": \"bold\"}, {\"type\": \"italic\"}]}]}]}";

        final TextMarkupDocument expected = jsonMapper.readValue(expectedJson, TextMarkupDocument.class);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldConvertMultilineItalicWithBold() throws JsonProcessingException {
        final String inputString = "_some text and then new line \n\nand **some** other text_";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        assertThat(result).isNotNull();

        final ObjectMapper jsonMapper = new JsonMapper();

        // The result should have italic marks applied to the entire text including the bold part
        // Note: The text is split into separate paragraphs due to newlines, which is correct behavior
        final String expectedJson = "{\"type\": \"doc\", \"content\": [{\"type\": \"paragraph\", \"attrs\": null, \"content\": [{\"text\": \"some text and then new line \", \"type\": \"text\", \"marks\": [{\"type\": \"italic\"}]}], \"attrs\": null}, {\"type\": \"paragraph\", \"attrs\": null, \"content\": [{\"text\": \"and\", \"type\": \"text\", \"marks\": [{\"type\": \"italic\"}]}, {\"text\": \" some \", \"type\": \"text\", \"marks\": [{\"type\": \"bold\"}, {\"type\": \"italic\"}]}, {\"text\": \"other text\", \"type\": \"text\", \"marks\": [{\"type\": \"italic\"}]}], \"attrs\": null}]}";

        final TextMarkupDocument expected = jsonMapper.readValue(expectedJson, TextMarkupDocument.class);

        assertThat(result).isEqualTo(expected);
    }
}
