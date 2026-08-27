package io.github.eroshenkoam.allure.textmarkup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import io.github.eroshenkoam.allure.client.dto.textmarkup.TextMarkupDocument;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

public class MarkdownToJsonConverterTestCase {

    
    @Test
    void shouldHandleMultipleSingleLineItalicsInOnePass() throws JsonProcessingException {
        final String inputString =
                "_В виджете абонента отображаются данные, полученные из функций_\n"
                        + "next line\n"
                        + "_Для переключателя в положении \"true\" выполняется запрос API SLS_";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        assertThat(json).doesNotContain("ITALICSTART").doesNotContain("ITALICEND");
        assertThat(json.split("\"italic\"")).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void shouldNotConvertUnderscoresInIdentifiers() throws JsonProcessingException {
        final String inputString = "**Setup step. Получение токена для пользователя `MGF_B2B_FCOKK`**";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        assertThat(result).isNotNull();

        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        assertThat(json).contains("MGF_B2B_FCOKK");
        // The string should not be split by underscores into separate italic segments
        assertThat(json).doesNotContain("\"text\":\"B2B\"");
        assertThat(json).contains("\"type\":\"bold\"");
        assertThat(json).contains("\"type\":\"code\"");
    }

    @Test
    void shouldHandleComplexMarkdownWithKeywords() throws JsonProcessingException {
        final String inputString = "**1.1. В состав бизнес-роли пользователя  должны входить следующие роли из "
                + "`keyword =` [[Keyword] Роли](https://allure.nexign.com/project/261/test-cases/453207):**  \n"
                + "*- Работа с карточкой договора*  \n"
                + "*- Работа с карточкой ЛС*  \n"
                + "*- Работа с бизнес-сущностями и соответствующими OAPI клиентской иерархии*";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        assertThat(result).isNotNull();

        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        assertThat(json).contains("\"type\":\"bold\"");
        assertThat(json).contains("\"type\":\"code\"");
        assertThat(json).contains("keyword =");
        assertThat(json).contains("\"type\":\"link\"");
        assertThat(json).contains("https://allure.nexign.com/project/261/test-cases/453207");
        assertThat(json).contains("\"type\":\"italic\"");
    }

    @Test
    void shouldHandleTablesAndParametersWithUnderscores() {
        final String inputString = "Использовать следующие входные данные:\n\n"
                + "| Параметр | Описание         | Значение по умолчанию         |\n"
                + "|---|---|---|\n"
                + "| jrtp_id  | 3                | Юридический тип клиента: 'ИП' |\n"
                + "| TOKEN    | (p2).{TOKEN_LIS} | Токен                         |";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        assertThat(result).isNotNull();

        final ObjectMapper jsonMapper = new JsonMapper();
        final String json;
        try {
            json = jsonMapper.writeValueAsString(result);
            assertThat(json).contains("jrtp_id");
            assertThat(json).contains("TOKEN_LIS");
            assertThat(json).doesNotContain("\"type\":\"italic\"");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldHandleCompleteTestCase1() {
        final String inputString = "**Setup step. Получение токена для пользователя `MGF_B2B_FCOKK`**\n\n"
                + "**1.1. В состав бизнес-роли пользователя  должны входить следующие роли из "
                + "`keyword =` [[Keyword] Роли](https://allure.nexign.com/project/261/test-cases/453207):**  \n"
                + "[Link to keyword](../../../../../library/asserts/sso/Common/check_roles_groups_list.md)\n"
                + "*- Работа с карточкой договора*  \n"
                + "*- Работа с карточкой ЛС*  \n"
                + "*- Работа с бизнес-сущностями и соответствующими OAPI клиентской иерархии*\n\n"
                + "**1.2. Выполнить `keyword =`[[Keyword] OAPI: Получение токена CCM_Portal "
                + "(/api/token)](https://allure.nexign.com/project/261/test-cases/453019).**  \n"
                + "[Link to keyword](../../../../../library/application/sso/Tokens/get_token.md)\n\n"
                + "Использовать следующие входные данные:\n\n"
                + "| Параметр | Описание                                     |\n"
                + "|---|---|\n"
                + "| LOGIN    | Логин пользователя CCM_PORTAL (MGF_B2B_FCOKK)  |\n"
                + "| PASSWORD | Пароль пользователя CCM_PORTAL (MGF_B2B_FCOKK) |\n\n"
                + "Зафиксировать:\n\n"
                + "| Параметр | Описание |\n"
                + "|---|---|\n"
                + "| TOKEN    | Токен    |";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        assertThat(result).isNotNull();

        final ObjectMapper jsonMapper = new JsonMapper();
        final String json;
        try {
            json = jsonMapper.writeValueAsString(result);
            assertThat(json).contains("MGF_B2B_FCOKK");
            assertThat(json).contains("CCM_Portal");
            assertThat(json).contains("keyword =");
            assertThat(json).contains("\"type\":\"bold\"");
            assertThat(json).contains("\"type\":\"italic\"");
            assertThat(json).contains("\"type\":\"code\"");
            assertThat(json).contains("\"type\":\"link\"");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldHandleCompleteTestCase2() {
        final String inputString = "**Setup step. Генерация ИНН для ИП c проверкой его отсутствия в базе**  \n"
                + "Выполнить **`keyword =` [[Keyword] Генерация ИНН и ОГРН и проверка, "
                + "что не существует клиента с этими данными](https://allure.nexign.com/project/261/test-cases/453621)**  \n"
                + "[Link to keyword](../../../../../library/parameters/data/Taxes/generate_inn_ogrn_and_check_not_exist.md)\n\n"
                + "Использовать следующие входные данные:\n\n"
                + "| Параметр | Описание         | Значение по умолчанию         |\n"
                + "|---|---|---|\n"
                + "| jrtp_id  | 3                | Юридический тип клиента: 'ИП' |\n"
                + "| TOKEN    | (p2).{TOKEN_LIS} | Токен                         |\n\n"
                + "Зафиксировать:\n\n"
                + "| Параметр | Описание |\n"
                + "|---|---|\n"
                + "| inn      | ИНН      |\n"
                + "| ogrn     | ОГРН     |";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        assertThat(result).isNotNull();

        final ObjectMapper jsonMapper = new JsonMapper();
        final String json;
        try {
            json = jsonMapper.writeValueAsString(result);
            assertThat(json).contains("jrtp_id");
            assertThat(json).contains("TOKEN_LIS");
            assertThat(json).contains("keyword =");
            assertThat(json).contains("\"type\":\"bold\"");
            assertThat(json).contains("\"type\":\"code\"");
            assertThat(json).contains("\"type\":\"link\"");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldMergeSplitItalicMarkers() throws JsonProcessingException {
        // Test that the post-processing correctly identifies and merges
        // split italic markers that may occur in complex nested formatting
        
        // This creates a scenario where bold text might end with * and the next node is just *
        // For example: "**text with *italic* inside**" might get split
        final String inputString = "**Для ручного тестирования *и для* автоматизации**";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        assertThat(result).isNotNull();

        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        // The result should have bold marks
        assertThat(json).contains("\"type\":\"bold\"");
        
        // Should not have multiple standalone * text nodes without marks
        // Count how many times we have "text":"*" with no other content
        final int starCount = json.split("\"text\":\"\\*\"").length - 1;
        // We expect at most the italic markers that are properly formatted
        assertThat(starCount).as("Should not have excessive standalone star nodes").isLessThan(3);
    }

    @Test
    void shouldMergeSplitItalicMarkersRealWorld() throws JsonProcessingException {
        // Test a real-world pattern that creates split italic markers
        // Bold text with italic inside: **text *italic* more**
        final String inputString = "** *Для ручного тестирования и для автоматизации* **";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        assertThat(result).isNotNull();

        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        // After post-processing, should not have standalone * nodes with null marks
        // Count occurrences of "text":"*" followed by "marks":null
        final boolean hasOrphanedStar = json.contains("\"text\":\"*\",\"type\":\"text\",\"marks\":null") ||
                                        json.contains("\"text\":\"*\",\"type\":\"text\",\"marks\":[]");
        
        assertThat(hasOrphanedStar).as("Should not have orphaned * nodes after post-processing").isFalse();
    }

    @Test
    void shouldMergeSplitItalicMarkersWithNextStartingStar() throws JsonProcessingException {
        // Test the pattern where italic closing * is at the start of the next text node
        // This pattern occurs in real migration data where italic markers span multiple nodes
        
        // Test with a pattern that will properly parse: Bold text containing italic
        final String inputString = "** *Text content here* ** more text";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        assertThat(result).isNotNull();

        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        // The post-processing should handle any split italic markers
        // Main assertion: should not have excessive orphaned star nodes
        final boolean hasProblematicPattern = 
            json.contains("\"text\":\"*\",\"type\":\"text\",\"marks\":null") ||
            json.contains("\"text\":\"*\",\"type\":\"text\",\"marks\":[]");
        
        assertThat(hasProblematicPattern)
            .as("Should not have orphaned * nodes with null marks after post-processing")
            .isFalse();
    }

    @Test
    void shouldConvertEverySingleLineItalicSplitByInlineCode() throws JsonProcessingException {
        final String inputString = "_Выполнен запрос `GET /api/v1` для проверки_\n"
                + "next line\n"
                + "_Второй запрос `POST /api/v2` тоже выполнен_";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        assertThat(json).doesNotContain("ITALICSTART").doesNotContain("ITALICEND");
        assertThat(json).contains("Второй запрос").contains("тоже выполнен");
    }

    @Test
    void shouldConvertEveryMultilineItalicInOneDocument() throws JsonProcessingException {
        final String inputString = "Заголовок\n"
                + "_Первая строка италика\n"
                + "вторая строка италика_\n"
                + "Обычный текст\n"
                + "_Третья строка италика\n"
                + "четвёртая строка италика_\n"
                + "Конец";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        assertThat(json).doesNotContain("ITALICSTART").doesNotContain("ITALICEND");
        assertThat(json).contains("Третья строка италика").contains("четвёртая строка италика");
        assertThat(json.split("\"italic\"")).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void shouldConvertEveryItalicSplitByLink() throws JsonProcessingException {
        final String inputString = "_См. [ссылку](http://example.com) здесь_\n"
                + "текст\n"
                + "_И [другую](http://example.org) тоже_";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        assertThat(json).doesNotContain("ITALICSTART").doesNotContain("ITALICEND");
        assertThat(json).contains("http://example.org");
    }

    @Test
    void shouldKeepTextAroundItalicMarkers() throws JsonProcessingException {
        final String inputString = "- Выполнен запрос _POST OAPI/v2/subscribers/{subscriberId}/subscriptionGroups/search_"
                + " - получение информации по сообществу в зависимости от роли";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        assertThat(json).doesNotContain("ITALICSTART").doesNotContain("ITALICEND");
        assertThat(json).contains("* Выполнен запрос ");
        assertThat(json).contains(" - получение информации по сообществу в зависимости от роли");
        assertThat(json).contains("\"type\":\"italic\"");
    }

    @Test
    void shouldDropItalicMarkerWithoutPair() throws JsonProcessingException {
        final String inputString = "_Открытый италик без закрывающего маркера";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        assertThat(json).doesNotContain("ITALICSTART").doesNotContain("ITALICEND");
        assertThat(json).contains("Открытый италик без закрывающего маркера");
    }

    @Test
    void shouldRenderListItemsWithAsteriskAndFourSpaceIndent() throws JsonProcessingException {
        final String inputString = "- Отображается несворачиваемый блок со следующими данными:\n"
                + "  - Объем организатора:\n"
                + "    - отображается \"безлимитно\", ЕСЛИ:\n"
                + "* Лимит сообщества";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        assertThat(json).contains("* Отображается несворачиваемый блок со следующими данными:");
        assertThat(json).contains("    * Объем организатора:");
        assertThat(json).contains("        * отображается ");
        assertThat(json).contains("* Лимит сообщества");
        assertThat(json).doesNotContain("- Объем организатора");
    }

    @Test
    void shouldNotReadListItemAsteriskAsItalicMarker() throws JsonProcessingException {
        final String inputString = "- пункт с *выделением* и `кодом`";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        // the marker is its own node, so it cannot open an italic span over the rest of the item
        assertThat(json).contains("\"text\":\"* \",\"marks\":null");
        assertThat(json).contains("\"text\":\"выделением\"");
        assertThat(json).contains("\"type\":\"italic\"");
        assertThat(json).contains("\"type\":\"code\"");
        assertThat(json).doesNotContain("\"text\":\"пункт с\",\"marks\":[{\"type\":\"italic\"");
    }

    @Test
    void shouldKeepNumberedListItemsUnchanged() throws JsonProcessingException {
        final String inputString = "1. Первый шаг\n2. Второй шаг";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        assertThat(json).contains("1. Первый шаг").contains("2. Второй шаг");
        assertThat(json).doesNotContain("*");
    }

    @Test
    void shouldKeepHeadingLevelAsHashesAndColourFirstLevel() throws JsonProcessingException {
        final String inputString = "# Заголовок первого уровня";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        assertThat(json).contains("# Заголовок первого уровня");
        assertThat(json).contains("\"type\":\"bold\"");
        assertThat(json).contains("\"type\":\"text_color\"");
        assertThat(json).contains("\"kind\":\"green\"");
    }

    @Test
    void shouldNotColourNestedHeadings() throws JsonProcessingException {
        final String inputString = "## Второй уровень\n### Третий уровень";

        final TextMarkupDocument result = MarkdownToJsonConverter.convertToJson(inputString);
        final ObjectMapper jsonMapper = new JsonMapper();
        final String json = jsonMapper.writeValueAsString(result);

        assertThat(json).contains("## Второй уровень");
        assertThat(json).contains("### Третий уровень");
        assertThat(json).contains("\"type\":\"bold\"");
        assertThat(json).doesNotContain("text_color");
    }

}
