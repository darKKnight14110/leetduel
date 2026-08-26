-- Dev seed data: enough real problems (with real function signatures) to
-- exercise the full judge loop end-to-end. Not production content.

INSERT INTO problem.problems (id, slug, title, description, difficulty, time_limit_ms, memory_limit_mb)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'two-sum',
    'Two Sum',
    'Given an array of integers nums and an integer target, return indices of the two numbers such that they add up to target. Exactly one valid answer exists.',
    'EASY',
    2000,
    256
);

INSERT INTO problem.function_signatures (id, problem_id, function_name, return_type)
VALUES (
    'aaaaaaaa-1111-1111-1111-111111111111',
    '11111111-1111-1111-1111-111111111111',
    'twoSum',
    'int[]'
);

INSERT INTO problem.parameters (function_signature_id, ordinal, name, type) VALUES
    ('aaaaaaaa-1111-1111-1111-111111111111', 0, 'nums', 'int[]'),
    ('aaaaaaaa-1111-1111-1111-111111111111', 1, 'target', 'int');

INSERT INTO problem.language_stubs (problem_id, language, stub_code) VALUES
    ('11111111-1111-1111-1111-111111111111', 'PYTHON',
     'def twoSum(nums, target):' || chr(10) || '    pass'),
    ('11111111-1111-1111-1111-111111111111', 'JAVA',
     'class Solution {' || chr(10) || '    public int[] twoSum(int[] nums, int target) {' || chr(10) || '        return new int[0];' || chr(10) || '    }' || chr(10) || '}');

-- ordinal 0: sample, shown on the problem page.
-- ordinal 1-2: hidden, judge-only.
INSERT INTO problem.test_cases (problem_id, ordinal, input, expected_output, is_sample) VALUES
    ('11111111-1111-1111-1111-111111111111', 0, '[[2,7,11,15],9]', '[0,1]', true),
    ('11111111-1111-1111-1111-111111111111', 1, '[[3,2,4],6]', '[1,2]', false),
    ('11111111-1111-1111-1111-111111111111', 2, '[[3,3],6]', '[0,1]', false);

INSERT INTO problem.problems (id, slug, title, description, difficulty, time_limit_ms, memory_limit_mb)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    'reverse-string-case',
    'Is Palindrome',
    'Given a string s, return true if it reads the same forward and backward, false otherwise.',
    'EASY',
    2000,
    256
);

INSERT INTO problem.function_signatures (id, problem_id, function_name, return_type)
VALUES (
    'aaaaaaaa-2222-2222-2222-222222222222',
    '22222222-2222-2222-2222-222222222222',
    'isPalindrome',
    'boolean'
);

INSERT INTO problem.parameters (function_signature_id, ordinal, name, type) VALUES
    ('aaaaaaaa-2222-2222-2222-222222222222', 0, 's', 'string');

INSERT INTO problem.language_stubs (problem_id, language, stub_code) VALUES
    ('22222222-2222-2222-2222-222222222222', 'PYTHON',
     'def isPalindrome(s):' || chr(10) || '    pass'),
    ('22222222-2222-2222-2222-222222222222', 'JAVA',
     'class Solution {' || chr(10) || '    public boolean isPalindrome(String s) {' || chr(10) || '        return false;' || chr(10) || '    }' || chr(10) || '}');

INSERT INTO problem.test_cases (problem_id, ordinal, input, expected_output, is_sample) VALUES
    ('22222222-2222-2222-2222-222222222222', 0, '["racecar"]', 'true', true),
    ('22222222-2222-2222-2222-222222222222', 1, '["hello"]', 'false', false);
