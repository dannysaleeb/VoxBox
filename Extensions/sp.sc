+ Number {
	sp {
		var overflow, tempNum, tempArray=[];

		tempNum = this.copy;

		4.do {
			overflow = tempNum % 10;
			tempNum = tempNum div: 10;
			tempArray = tempArray.addFirst(overflow);
		};

		^ScorePosition(tempArray[0], tempArray[1], tempArray[2], tempArray[3])
	}
}