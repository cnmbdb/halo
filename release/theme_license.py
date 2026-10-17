"""Fail closed when the shared licensing plugin is missing or stopped."""
import re


def guard_theme(files):
    for name in list(files):
        if not name.startswith('templates/') or not name.endswith('.html'):
            continue
        if name.count('/') != 1 and name != 'templates/error/error.html':
            continue
        text = files[name].decode()
        guard = ' th:replace="${pluginFinder.available(\'commerce\', \'>=0.2.9\')} ? _ : ~{modules/license-required :: html}"'
        text, count = re.subn(r'<html(?=[\s>])', '<html' + guard, text, count=1)
        assert count == 1, f'Missing HTML root in {name}'
        # Guard outer plugin-backed docs branches as well as standalone HTML roots.
        condition = "${pluginFinder.available('commerce', '>=0.2.9')}"
        text = '<th:block th:if="' + condition + '">\n' + text + '\n</th:block>\n' + '<th:block th:unless="' + condition + '"><th:block th:replace="~{modules/license-required :: html}" /></th:block>\n'
        files[name] = text.encode()
