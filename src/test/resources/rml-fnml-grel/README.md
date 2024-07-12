# GREL functions
## Boolean functions
- [x] and(bool a, bool b)
- [x] or(bool a, bool b)
- [x] not(bool a)
- [x] xor(bool a, bool b)

## String functions
- [X] length(s)
- [ ] toString(o, string format (optional))

## Testing string characteristics
- [X] startsWith(s, sub)
- [X] endsWith(s, sub)
- [X] contains(s, sub or p)

## Basic string modification
- [X] toLowercase(s)
- [X] toUppercase(s)
- [X] toTitlecase(s)

## Trimming
- [X] trim(s)
- [X] strip(s)
- [X] chomp(s, sep)

## Substring
- [x] substring(s, n from, n to (optional))
- [ ] slice(s, n from, n to (optional)) -- same as substring, leave out
- [x] get(s, n from, n to (optional))

## Find and replace
- [ ] indexOf(s, sub)
- [ ] lastIndexOf(s, sub)
- [x] replace(s, s find, s replace)
- [ ] replace(s, p find, s replace)
- [ ] replaceChars(s, s find, s replace)
- [ ] replaceEach(s, a find, a replace)
- [ ] find(s, sub or p)
- [ ] match(s, p)

## String parsing and splitting
- [ ] toNumber(s)
- [ ] split(s, s or p sep, b preserveTokens (optional))
- [ ] splitByLengths(s, n1, n2, ...)
- [ ] smartSplit(s, s or p sep (optional))
- [ ] splitByCharType(s)
- [ ] partition(s, s or p fragment, b omitFragment (optional))
- [ ] rpartition(s, s or p fragment, b omitFragment (optional))

## Encoding and hashing
- [ ] diff(s1, s2, s timeUnit (optional))
- [X] escape(s, s mode)
- [ ] unescape(s, s mode)
- [ ] encode(s, s encoding)
- [ ] decode(s, s encoding)
- [ ] md5(o)
- [ ] sha1(o)
- [ ] phonetic(s, s encoding)
- [ ] reinterpret(s, s encoderTarget, s encoderSource)
- [ ] fingerprint(s)
- [ ] ngram(s, n)
- [ ] ngramFingerprint(s, n)
- [ ] unicode(s)
- [ ] unicodeType(s)

## Translating
- [ ] detectLanguage(s)

## URI parsing
- [ ] parseUri(s)

## Array functions
- [ ] length(a)
- [ ] slice(a, n from, n to (optional))
- [ ] get(a, n from, n to (optional))
- [ ] inArray(a, s)
- [ ] reverse(a)
- [ ] sort(a)
- [ ] sum(a)
- [ ] join(a, sep)
- [ ] uniques(a)

## Date functions
- [ ] now()
- [ ] toDate(o, b monthFirst, s format1, s format2, ...)
- [ ] diff(d1, d2, s timeUnit)
- [ ] inc(d, n, s timeUnit)
- [ ] datePart(d, s timeUnit)

## Math functions
- [X] abs(n)	Returns the absolute value of a number.	abs(-6) returns 6.
- [ ] acos(n)	Returns the arc cosine of an angle, in the range 0 through PI.	acos(0.345) returns 1.218557541697832.
- [ ] asin(n)	Returns the arc sine of an angle in the range of -PI/2 through PI/2.	asin(0.345) returns 0.35223878509706474.
- [ ] atan(n)	Returns the arc tangent of an angle in the range of -PI/2 through PI/2.	atan(0.345) returns 0.3322135507465967.
- [ ] atan2(n1, n2)	Converts rectangular coordinates (n1, n2) to polar (r, theta). Returns number theta.	atan2(0.345,0.6) returns 0.5218342798144103.
- [X] ceil(n)	Returns the ceiling of a number.	3.7.ceil() returns 4 and -3.7.ceil() returns -3.
- [ ] combin(n1, n2)	Returns the number of combinations for n2 elements as divided into n1.	combin(20,2) returns 190.
- [ ] cos(n)	Returns the trigonometric cosine of a value.	cos(5) returns 0.28366218546322625.
- [ ] cosh(n)	Returns the hyperbolic cosine of a value.	cosh(5) returns 74.20994852478785.
- [ ] degrees(n)	Converts an angle from radians to degrees.	degrees(5) returns 286.4788975654116.
- [ ] even(n)	Rounds the number up to the nearest even integer.	even(5) returns 6.
- [ ] exp(n)	Returns e raised to the power of n.	exp(5) returns 148.4131591025766.
- [ ] fact(n)	Returns the factorial of a number, starting from 1.	fact(5) returns 120.
- [ ] factn(n1, n2)	Returns the factorial of n1, starting from n2.	factn(10,3) returns 280.
- [X] floor(n)	Returns the floor of a number.	3.7.floor() returns 3 and -3.7.floor() returns -4.
- [ ] gcd(n1, n2)	Returns the greatest common denominator of two numbers.	gcd(95,135) returns 5.
- [ ] lcm(n1, n2)	Returns the least common multiple of two numbers.	lcm(95,135) returns 2565.
- [ ] ln(n)	Returns the natural logarithm of n.	ln(5) returns 1.6094379124341003.
- [ ] log(n)	Returns the base 10 logarithm of n.	log(5) returns 0.6989700043360189.
- [ ] max(n1, n2)	Returns the larger of two numbers.	max(3,10) returns 10.
- [ ] min(n1, n2)	Returns the smaller of two numbers.	min(3,10) returns 3.
- [ ] mod(n1, n2)	Returns n1 modulus n2. Note: value.mod(9) will work, whereas 74.mod(9) will not work.	mod(74, 9) returns 2.
- [ ] multinomial(n1, n2 …(optional))	Calculates the multinomial of one number or a series of numbers.	multinomial(2,3) returns 10.
- [ ] odd(n)	Rounds the number up to the nearest odd integer.	odd(10) returns 11.
- [ ] pow(n1, n2)	Returns n1 raised to the power of n2. Note: value.pow(3)will work, whereas2.pow(3)` will not work.	pow(2, 3) returns 8 (2 cubed) and pow(3, 2) returns 9 (3 squared). The square root of any numeric value can be called with value.pow(0.5).
- [ ] quotient(n1, n2)	Returns the integer portion of a division (truncated, not rounded), when supplied with a numerator and denominator.	quotient(9,2) returns 4.
- [ ] radians(n)	Converts an angle in degrees to radians.	radians(10) returns 0.17453292519943295.
- [ ] random(n lowerBound, n upperBound)	Returns a random integer in the interval between the lower and upper bounds (inclusively). Will output a different random number in each cell in a column. If no arguments are provided, returns a number in the range 0.0 <= x < 1.0
- [ ] round(n)	Rounds a number to the nearest integer.	3.7.round() returns 4 and -3.7.round() returns -4.
- [ ] sin(n)	Returns the trigonometric sine of an angle.	sin(10) returns -0.5440211108893698.
- [ ] sinh(n)	Returns the hyperbolic sine of an angle.	sinh(10) returns 11013.232874703393.
- [ ] sum(a)	Sums the numbers in an array. Ignores non-number items. Returns 0 if the array does not contain numbers.	sum([ 10, 2, three ]) returns 12.
- [ ] tan(n)	Returns the trigonometric tangent of an angle.	tan(10) returns 0.6483608274590866.
- [ ] tanh(n)	Returns the hyperbolic tangent of a value.	tanh(10) returns 0.9999999958776927.

## Other functions
- [ ] type(o)
- [ ] facetCount(choiceValue, s facetExpression, s columnName)
- [ ] hasField(o, s name)
- [ ] coalesce(o1, o2, o3, ...)
- [ ] cross(cell, s projectName (optional), s columnName (optional))