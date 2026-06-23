CREATE TABLE IF NOT EXISTS insurance_packages (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name_en VARCHAR(255) NOT NULL,
    name_es VARCHAR(255) NOT NULL,
    name_tr VARCHAR(255) NOT NULL,
    description_en VARCHAR(1000) NOT NULL,
    description_es VARCHAR(1000) NOT NULL,
    description_tr VARCHAR(1000) NOT NULL,
    price_per_day NUMERIC(10, 2) NOT NULL,
    deposit_amount NUMERIC(10, 2) NOT NULL,
    display_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    recommended BOOLEAN NOT NULL,
    badge_en VARCHAR(255),
    badge_es VARCHAR(255),
    badge_tr VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS insurance_coverage_items (
    id BIGSERIAL PRIMARY KEY,
    insurance_package_id BIGINT NOT NULL REFERENCES insurance_packages(id),
    title_en VARCHAR(255) NOT NULL,
    title_es VARCHAR(255) NOT NULL,
    title_tr VARCHAR(255) NOT NULL,
    description_en VARCHAR(1000) NOT NULL,
    description_es VARCHAR(1000) NOT NULL,
    description_tr VARCHAR(1000) NOT NULL,
    included BOOLEAN NOT NULL,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

ALTER TABLE rental_booking_details
    ADD COLUMN IF NOT EXISTS insurance_package_id BIGINT REFERENCES insurance_packages(id),
    ADD COLUMN IF NOT EXISTS insurance_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS insurance_name_snapshot VARCHAR(255),
    ADD COLUMN IF NOT EXISTS insurance_daily_price_snapshot NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS insurance_total_snapshot NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deposit_amount_snapshot NUMERIC(10, 2) NOT NULL DEFAULT 0;

INSERT INTO insurance_packages (
    code, name_en, name_es, name_tr, description_en, description_es, description_tr,
    price_per_day, deposit_amount, display_order, active, recommended, badge_en, badge_es, badge_tr
)
VALUES
('BASIC', 'Basic Protection', 'Protección básica', 'Temel Koruma',
 'Included protection with a higher refundable deposit.',
 'Protección incluida con un depósito reembolsable más alto.',
 'Daha yüksek iade edilebilir depozito ile dahil koruma.',
 0.00, 750.00, 1, true, false, 'Included', 'Incluido', 'Dahil'),
('FULL', 'Full Protection', 'Protección completa', 'Tam Koruma',
 'Enhanced protection with lower deposit and broader coverage.',
 'Protección ampliada con menor depósito y cobertura más amplia.',
 'Daha düşük depozito ve genişletilmiş kapsamla gelişmiş koruma.',
 21.00, 300.00, 2, true, true, 'Recommended', 'Recomendado', 'Önerilir')
ON CONFLICT (code) DO NOTHING;

INSERT INTO insurance_coverage_items (
    insurance_package_id, title_en, title_es, title_tr, description_en, description_es, description_tr, included, display_order
)
SELECT p.id, v.title_en, v.title_es, v.title_tr, v.description_en, v.description_es, v.description_tr, v.included, v.display_order
FROM insurance_packages p
JOIN (VALUES
    ('BASIC', 'Collision damage waiver', 'Cobertura por daños de colisión', 'Çarpışma hasarı muafiyeti', 'Standard collision damage coverage with excess.', 'Cobertura estándar por daños de colisión con franquicia.', 'Muafiyetli standart çarpışma hasarı koruması.', true, 1),
    ('BASIC', 'Theft protection', 'Protección contra robo', 'Hırsızlık koruması', 'Protection if the vehicle is stolen during the rental.', 'Protección si el vehículo es robado durante el alquiler.', 'Kiralama sırasında aracın çalınmasına karşı koruma.', true, 2),
    ('BASIC', 'Roadside assistance', 'Asistencia en carretera', 'Yol yardımı', '24/7 support for breakdowns and urgent issues.', 'Soporte 24/7 para averías e incidencias urgentes.', 'Arıza ve acil durumlar için 7/24 destek.', true, 3),
    ('BASIC', 'Glass and tire damage', 'Daños en cristales y neumáticos', 'Cam ve lastik hasarı', 'Glass, tire, and underbody damage are not reduced.', 'Los daños en cristales, neumáticos y bajos no se reducen.', 'Cam, lastik ve alt gövde hasarlarında indirim yoktur.', false, 4),
    ('BASIC', 'Reduced deposit', 'Depósito reducido', 'Azaltılmış depozito', 'Standard deposit applies at pickup.', 'Se aplica el depósito estándar en la recogida.', 'Teslim alma sırasında standart depozito uygulanır.', false, 5),
    ('FULL', 'Collision damage waiver', 'Cobertura por daños de colisión', 'Çarpışma hasarı muafiyeti', 'Enhanced collision damage coverage with lower excess.', 'Cobertura ampliada por daños de colisión con menor franquicia.', 'Daha düşük muafiyetli geliştirilmiş çarpışma hasarı koruması.', true, 1),
    ('FULL', 'Theft protection', 'Protección contra robo', 'Hırsızlık koruması', 'Protection if the vehicle is stolen during the rental.', 'Protección si el vehículo es robado durante el alquiler.', 'Kiralama sırasında aracın çalınmasına karşı koruma.', true, 2),
    ('FULL', 'Roadside assistance', 'Asistencia en carretera', 'Yol yardımı', '24/7 support for breakdowns and urgent issues.', 'Soporte 24/7 para averías e incidencias urgentes.', 'Arıza ve acil durumlar için 7/24 destek.', true, 3),
    ('FULL', 'Glass and tire damage', 'Daños en cristales y neumáticos', 'Cam ve lastik hasarı', 'Glass, tire, and underbody damage are included.', 'Cristales, neumáticos y bajos están incluidos.', 'Cam, lastik ve alt gövde hasarları dahildir.', true, 4),
    ('FULL', 'Reduced deposit', 'Depósito reducido', 'Azaltılmış depozito', 'Lower refundable deposit at pickup.', 'Depósito reembolsable más bajo en la recogida.', 'Teslim alma sırasında daha düşük iade edilebilir depozito.', true, 5)
) AS v(code, title_en, title_es, title_tr, description_en, description_es, description_tr, included, display_order)
ON p.code = v.code
WHERE NOT EXISTS (
    SELECT 1 FROM insurance_coverage_items existing
    WHERE existing.insurance_package_id = p.id
      AND existing.display_order = v.display_order
);
