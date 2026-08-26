from pathlib import Path

path = Path('AdvancedCore/src/main/java/com/bencodez/advancedcore/api/javascript/JavascriptPlaceholderBinder.java')
text = path.read_text()

resolve_start = text.index('    private static String resolve(String token, OfflinePlayer player, Map<String, String> placeholders) {')
resolve_end = text.index('    private static Object coerce(String value) {', resolve_start)
resolve_method = '''    private static String resolve(String token, OfflinePlayer player, Map<String, String> placeholders) {
        AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

        // Preserve the historical replacement order: AdvancedCore custom/reward
        // placeholders win name collisions, then PlaceholderAPI is applied to the
        // selected custom value so custom placeholders may themselves contain PAPI.
        if (placeholders != null) {
            String name = token.substring(1, token.length() - 1);
            for (Entry<String, String> entry : placeholders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    String value = entry.getValue();
                    if (value != null && player != null && plugin != null && plugin.isPlaceHolderAPIEnabled()) {
                        String resolved = PlaceholderAPI.setPlaceholders(player, value);
                        if (resolved != null) {
                            value = resolved;
                        }
                    }
                    return value;
                }
            }
        }

        // Only consult PlaceholderAPI for the original token when no custom
        // placeholder with the same name was supplied.
        if (token.startsWith("%") && player != null && plugin != null && plugin.isPlaceHolderAPIEnabled()) {
            String resolved = PlaceholderAPI.setPlaceholders(player, token);
            if (resolved != null && !resolved.equals(token)) {
                return resolved;
            }
        }
        return token;
    }

'''
text = text[:resolve_start] + resolve_method + text[resolve_end:]

regex_start = text.index('        private static boolean canStartRegex(String source, int slashIndex) {')
regex_end = text.index('        private static void addPatternRanges(String source, Pattern pattern, List<Range> target,', regex_start)
regex_helpers = '''        private static boolean canStartRegex(String source, int slashIndex) {
            int previousIndex = slashIndex - 1;
            while (previousIndex >= 0 && Character.isWhitespace(source.charAt(previousIndex))) {
                previousIndex--;
            }
            if (previousIndex < 0) {
                return true;
            }

            char previous = source.charAt(previousIndex);
            if ("([{:;,=!?&|+-*%^~<>".indexOf(previous) >= 0) {
                return true;
            }
            if (previous == ')' && closesControlStatementHead(source, previousIndex)) {
                return true;
            }

            if (Character.isJavaIdentifierPart(previous)) {
                int end = previousIndex + 1;
                int start = previousIndex;
                while (start >= 0 && Character.isJavaIdentifierPart(source.charAt(start))) {
                    start--;
                }
                String word = source.substring(start + 1, end);
                return word.equals("return") || word.equals("case") || word.equals("throw")
                        || word.equals("else") || word.equals("do") || word.equals("yield")
                        || word.equals("await") || word.equals("typeof") || word.equals("void")
                        || word.equals("delete") || word.equals("instanceof") || word.equals("in")
                        || word.equals("new");
            }
            return false;
        }

        private static boolean closesControlStatementHead(String source, int closeParen) {
            List<Integer> openingParens = new ArrayList<>();
            for (int i = 0; i <= closeParen; i++) {
                char current = source.charAt(i);
                if (current == '\\'' || current == '"') {
                    i = skipQuotedLiteral(source, i, closeParen + 1, current);
                    continue;
                }
                if (current == '`') {
                    i = skipTemplateLiteral(source, i, closeParen + 1);
                    continue;
                }
                if (current == '/' && canStartRegex(source, i)) {
                    int regexEnd = skipRegexLiteral(source, i, closeParen + 1);
                    if (regexEnd > i) {
                        i = regexEnd;
                        continue;
                    }
                }
                if (current == '(') {
                    openingParens.add(i);
                } else if (current == ')') {
                    if (openingParens.isEmpty()) {
                        return false;
                    }
                    int openingParen = openingParens.remove(openingParens.size() - 1);
                    if (i == closeParen) {
                        return isControlKeywordBefore(source, openingParen);
                    }
                }
            }
            return false;
        }

        private static boolean isControlKeywordBefore(String source, int openingParen) {
            int end = openingParen - 1;
            while (end >= 0 && Character.isWhitespace(source.charAt(end))) {
                end--;
            }
            if (end < 0 || !Character.isJavaIdentifierPart(source.charAt(end))) {
                return false;
            }

            int start = end;
            while (start >= 0 && Character.isJavaIdentifierPart(source.charAt(start))) {
                start--;
            }
            String word = source.substring(start + 1, end + 1);
            if (word.equals("if") || word.equals("while") || word.equals("for") || word.equals("with")
                    || word.equals("switch") || word.equals("catch")) {
                return true;
            }

            // Modern JavaScript may use `for await (...)`.
            if (!word.equals("await")) {
                return false;
            }
            end = start;
            while (end >= 0 && Character.isWhitespace(source.charAt(end))) {
                end--;
            }
            start = end;
            while (start >= 0 && Character.isJavaIdentifierPart(source.charAt(start))) {
                start--;
            }
            return end >= 0 && source.substring(start + 1, end + 1).equals("for");
        }

'''
text = text[:regex_start] + regex_helpers + text[regex_end:]
path.write_text(text)
