INSERT INTO users (
    company_code,
    name,
    employee_number,
    hire_date,
    role,
    created_at,
    updated_at
)
VALUES
    (
        'WB0001',
        '김하늘',
        '20250001',
        '2025-01-01',
        'ADMIN',
        NOW(),
        NOW()
    ),
    (
        'WB0002',
        '이서윤',
        '20240001',
        '2024-01-01',
        'ADMIN',
        NOW(),
        NOW()
    )
    ON DUPLICATE KEY UPDATE
                         name = VALUES(name),
                         hire_date = VALUES(hire_date),
                         role = VALUES(role),
                         updated_at = NOW();