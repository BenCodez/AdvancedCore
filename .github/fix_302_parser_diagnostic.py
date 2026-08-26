from pathlib import Path

p = Path('AdvancedCore/src/main/java/com/bencodez/advancedcore/api/javascript/JavascriptPlaceholderBinder.java')
text = p.read_text()
old = '''                Object parser = createParser(parserClass);
                Object diagnostic = Proxy.newProxyInstance(loader, new Class<?>[] { diagnosticClass },
                        (proxy, method, args) -> null);
                Method parse = parserClass.getMethod("parse", String.class, String.class, diagnosticClass);
                Object root = parse.invoke(parser, "AdvancedCore", source, diagnostic);
                if (root != null) {
                    contexts.parsed = true;
                    walk(root, treeClass, contexts, new IdentityHashMap<>());
                }
'''
new = '''                Object parser = createParser(parserClass);
                boolean[] hadParseDiagnostic = new boolean[1];
                Object diagnostic = Proxy.newProxyInstance(loader, new Class<?>[] { diagnosticClass },
                        (proxy, method, args) -> {
                            if ("report".equals(method.getName())) {
                                hadParseDiagnostic[0] = true;
                            }
                            return null;
                        });
                Method parse = parserClass.getMethod("parse", String.class, String.class, diagnosticClass);
                Object root = parse.invoke(parser, "AdvancedCore", source, diagnostic);
                if (root != null && !hadParseDiagnostic[0]) {
                    contexts.parsed = true;
                    walk(root, treeClass, contexts, new IdentityHashMap<>());
                }
'''
count = text.count(old)
if count != 1:
    raise SystemExit(f'parse diagnostic block: expected 1 match, got {count}')
p.write_text(text.replace(old, new, 1))
