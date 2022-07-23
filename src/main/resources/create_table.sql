DROP TABLE IF EXISTS product CASCADE;
CREATE TABLE product (
	id SERIAL,
	"name" varchar NOT NULL,
	description varchar NOT NULL,
	brand varchar NOT NULL,
	category_id integer NOT NULL,
	owner_id integer NOT NULL,
	price int4 NOT NULL,
	updated timestamp NOT NULL DEFAULT now(),
	CONSTRAINT product_pk PRIMARY KEY (id)
);
