
CREATE CACHED TABLE user (
  user_id BIGINT IDENTITY,
  email_addr VARCHAR(255) UNIQUE,
  password VARCHAR(255)
);

CREATE CACHED TABLE count_sheet (
  count_sheet_id BIGINT IDENTITY,
  creator_user_id BIGINT REFERENCES user(user_id),
  created_on TIMESTAMP NOT NULL,
  final_on TIMESTAMP NULL
);

CREATE CACHED TABLE category (
  category_id BIGINT IDENTITY,
  name VARCHAR(32) NOT NULL
);

INSERT INTO category(name) values('Pledge');
INSERT INTO category(name) values('Altar Guild');
INSERT INTO category(name) values('Holiday Flowers');
INSERT INTO category(name) values('Capital Campaign');
INSERT INTO category(name) values('Food Pantry');
INSERT INTO category(name) values('J2A - Youth Trip');
INSERT INTO category(name) values('Memorial Fund');
INSERT INTO category(name) values('Rector''s Discretionary');
INSERT INTO category(name) values('Other (see notes)');

CREATE CACHED TABLE contributor (
  contributor_id BIGINT IDENTITY,
  name VARCHAR(64) NOT NULL
);

CREATE CACHED TABLE deposit_item (
  item_id BIGINT IDENTITY,
  count_sheet_id BIGINT REFERENCES count_sheet(count_sheet_id),
  contributor_id BIGINT REFERENCES contributor(contributor_id),
  category_id BIGINT REFERENCES category(category_id),
  amount NUMERIC(9,2) NOT NULL,
  notes VARCHAR(1024)
);
