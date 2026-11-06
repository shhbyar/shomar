"""Domain errors raised by services. They know nothing about HTTP;
app/api/errors.py maps them to responses."""


class DomainError(Exception):
    pass


class NotFoundError(DomainError):
    pass


class ConflictError(DomainError):
    pass


class AuthenticationError(DomainError):
    pass


class InvalidInputError(DomainError):
    """The request is well-formed but breaks a business rule (e.g. refers to a
    category that doesn't exist or belongs to someone else)."""
