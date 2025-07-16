+ Object {
    >>> { |other|
        other.respondsTo(\input_).if {
            other.input = this;
        } {
            "Cannot patch into %, it has no .input_ method.".format(other.class).warn;
        };
        ^other
    }
}